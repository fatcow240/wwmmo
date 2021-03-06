package au.com.codeka.warworlds.server.ctrl;

import java.sql.Statement;
import java.util.ArrayList;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.codeka.common.model.BaseBuildRequest;
import au.com.codeka.common.model.BaseBuilding;
import au.com.codeka.common.model.BaseFleet;
import au.com.codeka.common.model.BuildingDesign;
import au.com.codeka.common.model.Design;
import au.com.codeka.common.model.Design.Dependency;
import au.com.codeka.common.model.DesignKind;
import au.com.codeka.common.model.ShipDesign;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.BuildRequest;
import au.com.codeka.warworlds.server.model.Building;
import au.com.codeka.warworlds.server.model.Colony;
import au.com.codeka.warworlds.server.model.DesignManager;
import au.com.codeka.warworlds.server.model.Fleet;
import au.com.codeka.warworlds.server.model.Star;

public class BuildQueueController {
    private static final Logger log = LoggerFactory.getLogger(BuildQueueController.class);
    private DataBase db;

    public BuildQueueController() {
        db = new DataBase();
    }
    public BuildQueueController(Transaction trans) {
        db = new DataBase(trans);
    }

    public void build(BuildRequest buildRequest) throws RequestException {
        Star star = new StarController(db.getTransaction()).getStar(buildRequest.getStarID());
        Colony colony = star.getColony(buildRequest.getColonyID());

        Design design = DesignManager.i.getDesign(buildRequest.getDesignKind(),
                buildRequest.getDesignID());

        if (buildRequest.getCount() <= 0) {
            throw new RequestException(400, "Cannot build negative count.");
        }
        if (buildRequest.getDesignKind() == DesignKind.SHIP
                && buildRequest.getCount() > design.getBuildCost().getMaxCount()
                && buildRequest.getExistingFleetID() == null) {
            buildRequest.setCount(design.getBuildCost().getMaxCount());
        }

        // check dependencies
        for (Design.Dependency dependency : design.getDependencies()) {
            if (!dependency.isMet(colony)) {
                throw new RequestException(400,
                        Messages.GenericError.ErrorCode.CannotBuildDependencyNotMet,
                        String.format("Cannot build %s as level %d %s is required.",
                                      buildRequest.getDesign().getDisplayName(),
                                      dependency.getLevel(),
                                      design.getDisplayName()));
            }
        }

        // check build limits
        if (design.getDesignKind() == DesignKind.BUILDING
                && buildRequest.getExistingBuildingKey() == null) {
            BuildingDesign buildingDesign = (BuildingDesign) design;

            if (buildingDesign.getMaxPerColony() > 0) {
                int maxPerColony = buildingDesign.getMaxPerColony();
                int numThisColony = 0;
                for (BaseBuilding building : colony.getBuildings()) {
                    if (building.getDesignID().equals(buildRequest.getDesignID())) {
                        numThisColony ++;
                    }
                }
                for (BaseBuildRequest baseBuildRequest : star.getBuildRequests()) {
                    BuildRequest otherBuildRequest = (BuildRequest) baseBuildRequest;
                    if (otherBuildRequest.getColonyID() == colony.getID() &&
                        otherBuildRequest.getDesignID().equals(buildRequest.getDesignID())) {
                        numThisColony ++;
                    }
                }

                if (numThisColony >= maxPerColony) {
                    throw new RequestException(400,
                            Messages.GenericError.ErrorCode.CannotBuildMaxPerColonyReached,
                            String.format("Cannot build %s, maximum per colony reached.",
                                          buildRequest.getDesign().getDisplayName()));
                }
            }

            if (buildingDesign.getMaxPerEmpire() > 0) {
                String sql ="SELECT (" +
                              " SELECT COUNT(*)" +
                              " FROM buildings" +
                              " WHERE empire_id = ?" +
                                " AND design_id = ?" +
                            " ) + (" +
                              " SELECT COUNT(*)" +
                              " FROM build_requests" +
                              " WHERE empire_id = ?" +
                                " AND design_id = ?" +
                            " )";
                try(SqlStmt stmt = db.prepare(sql)) {
                    stmt.setInt(1, buildRequest.getEmpireID());
                    stmt.setString(2, buildRequest.getDesignID());
                    stmt.setInt(3, buildRequest.getEmpireID());
                    stmt.setString(4, buildRequest.getDesignID());
                    Long numPerEmpire = stmt.selectFirstValue(Long.class);
                    if (numPerEmpire >= buildingDesign.getMaxPerEmpire()) {
                        throw new RequestException(400,
                                Messages.GenericError.ErrorCode.CannotBuildMaxPerColonyReached,
                                String.format("Cannot build %s, maximum per empire reached.",
                                              buildRequest.getDesign().getDisplayName()));
                    }
                } catch (Exception e) {
                    throw new RequestException(e);
                }
            }
        }

        if (buildRequest.getDesignKind() == DesignKind.BUILDING
                && buildRequest.getExistingBuildingKey() != null) {
            BuildingDesign buildingDesign = (BuildingDesign) design;

            // if we're upgrading a building, make sure we don't upgrade it twice!
            for (BaseBuildRequest baseBuildRequest : star.getBuildRequests()) {
                BuildRequest otherBuildRequest = (BuildRequest) baseBuildRequest;
                if (otherBuildRequest.getExistingBuildingKey() == null) {
                    continue;
                }
                if (otherBuildRequest.getExistingBuildingID() == buildRequest.getExistingBuildingID()) {
                    throw new RequestException(400,
                            Messages.GenericError.ErrorCode.CannotBuildDependencyNotMet,
                            String.format("Cannot upgrade %s, upgrade is already in progress.",
                                          buildRequest.getDesign().getDisplayName()));
                }
            }

            Building existingBuilding = null;
            for (BaseBuilding baseBuilding : colony.getBuildings()) {
                if (baseBuilding.getKey().equals(buildRequest.getExistingBuildingKey())) {
                    existingBuilding = (Building) baseBuilding;
                }
            }
            if (existingBuilding == null) {
                throw new RequestException(400,
                        Messages.GenericError.ErrorCode.CannotBuildDependencyNotMet,
                        String.format("Cannot upgrade %s, original building no longer exists.",
                                      buildRequest.getDesign().getDisplayName()));
            }

            // make sure the existing building isn't already at the maximum level
            if (existingBuilding.getLevel() == buildingDesign.getUpgrades().size() + 1) {
               throw new RequestException(400,
                        Messages.GenericError.ErrorCode.CannotBuildMaxLevelReached,
                        String.format("Cannot update %s, already at maximum level.",
                                buildRequest.getDesign().getDisplayName()));
            }

            // check dependencies for this specific level
            ArrayList<Dependency> dependencies = buildingDesign.getDependencies(
                    existingBuilding.getLevel() + 1);
            for (Design.Dependency dependency : dependencies) {
                if (!dependency.isMet(colony)) {
                    throw new RequestException(400,
                            Messages.GenericError.ErrorCode.CannotBuildDependencyNotMet,
                            String.format("Cannot upgrade %s as level %d %s is required.",
                                          buildRequest.getDesign().getDisplayName(),
                                          dependency.getLevel(),
                                          design.getDisplayName()));
                }
            }
        }

        if (buildRequest.getDesignKind() == DesignKind.SHIP
                && buildRequest.getExistingFleetID() != null) {
            ShipDesign shipDesign = (ShipDesign) design;
            log.info("BUILD : checking other upgrades for this ship. buildRequest.getExistingFleetID() = " + buildRequest.getExistingFleetID());

            // if we're upgrading a ship, make sure we don't upgrade it twice!
            for (BaseBuildRequest baseBuildRequest : star.getBuildRequests()) {
                BuildRequest otherBuildRequest = (BuildRequest) baseBuildRequest;
                if (otherBuildRequest.getExistingFleetID() == null) {
                    log.info("BUILD : otherBuildRequest has existingFleetID == null");
                    continue;
                }
                if ((int) otherBuildRequest.getExistingFleetID()
                        == (int) buildRequest.getExistingFleetID()) {
                    throw new RequestException(400,
                            Messages.GenericError.ErrorCode.CannotBuildDependencyNotMet,
                            String.format("Cannot upgrade %s, upgrade is already in progress.",
                                    shipDesign.getDisplayName()));
                } else {
                    log.info("BUILD : otherBuildRequest.getExistingFleetID() == "+otherBuildRequest.getExistingFleetID());
                }
            }

            Fleet existingFleet = null;
            for (BaseFleet baseFleet : star.getFleets()) {
                Fleet fleet = (Fleet) baseFleet;
                if (fleet.getID() == buildRequest.getExistingFleetID()) {
                    existingFleet = fleet;
                }
            }
            if (existingFleet == null) {
                throw new RequestException(400,
                        Messages.GenericError.ErrorCode.CannotBuildDependencyNotMet,
                        String.format("Cannot upgrade %s, original fleet no longer exists.",
                                shipDesign.getDisplayName()));
            }

            // make sure the existing fleet doesn't already have the upgrade
            if (existingFleet.getUpgrade(buildRequest.getUpgradeID()) != null) {
                throw new RequestException(400,
                        Messages.GenericError.ErrorCode.CannotBuildMaxLevelReached,
                        String.format("Cannot update %s, already has upgrade.",
                                shipDesign.getDisplayName()));
            }
        }

        // OK, we're good to go, let's go!
        String sql = "INSERT INTO build_requests (star_id, planet_index, colony_id, empire_id," +
                       " existing_building_id, design_kind, design_id, notes," +
                       " existing_fleet_id, upgrade_id, count, progress, processing, start_time," +
                       " end_time)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)";
        try (SqlStmt stmt = db.prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, buildRequest.getStarID());
            stmt.setInt(2, buildRequest.getPlanetIndex());
            stmt.setInt(3, buildRequest.getColonyID());
            stmt.setInt(4, buildRequest.getEmpireID());
            if (buildRequest.getExistingBuildingKey() != null) {
                stmt.setInt(5, buildRequest.getExistingBuildingID());
            } else {
                stmt.setNull(5);
            }
            stmt.setInt(6, buildRequest.getDesignKind().getValue());
            stmt.setString(7, buildRequest.getDesignID());
            stmt.setString(8, buildRequest.getNotes());
            stmt.setInt(9, buildRequest.getExistingFleetID());
            stmt.setString(10, buildRequest.getUpgradeID());
            stmt.setInt(11, buildRequest.getCount());
            stmt.setDouble(12, buildRequest.getProgress(false));
            stmt.setDateTime(13, buildRequest.getStartTime());
            stmt.setDateTime(14, buildRequest.getEndTime());
            stmt.update();
            buildRequest.setID(stmt.getAutoGeneratedID());
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void updateNotes(int buildRequestID, String notes) throws RequestException {
        String sql = "UPDATE build_requests SET notes = ? WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setString(1, notes);
            stmt.setInt(2, buildRequestID);
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void stop(Star star, BuildRequest buildRequest) throws RequestException {
        star.getBuildRequests().remove(buildRequest);

        String sql = "DELETE FROM build_requests WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setInt(1, buildRequest.getID());
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    /**
     * Accelerate the given build. Returns {@code true} if the build is now complete.
     */
    public boolean accelerate(Star star, BuildRequest buildRequest, float accelerateAmount) throws RequestException {
        if (accelerateAmount > 0.99f) {
            accelerateAmount = 1.0f;
        }
        float remainingProgress = 1.0f - buildRequest.getProgress(false);
        float progressToComplete = remainingProgress * accelerateAmount;

        Design design = buildRequest.getDesign();
        float mineralsToUse = design.getBuildCost().getCostInMinerals() * progressToComplete;
        float cost = mineralsToUse * buildRequest.getCount();

        Messages.CashAuditRecord.Builder audit_record_pb = Messages.CashAuditRecord.newBuilder();
        audit_record_pb.setEmpireId(buildRequest.getEmpireID());
        audit_record_pb.setBuildDesignId(buildRequest.getDesignID());
        audit_record_pb.setBuildCount(buildRequest.getCount());
        audit_record_pb.setAccelerateAmount(accelerateAmount);
        if (!new EmpireController().withdrawCash(buildRequest.getEmpireID(), cost, audit_record_pb)) {
            throw new RequestException(400, Messages.GenericError.ErrorCode.InsufficientCash,
                    "You don't have enough cash to accelerate this build.");
        }
        float finalProgress = buildRequest.getProgress(false) + progressToComplete;
        buildRequest.setProgress(finalProgress);
        if (finalProgress > 0.999) {
            buildRequest.setEndTime(DateTime.now());

            // if you accelerate to completion, don't spam a notification
            buildRequest.disableNotification();
            return true;
        }

        return false;
    }

    public void saveBuildRequest(BuildRequest buildRequest) throws RequestException {
        String sql = "UPDATE build_requests SET progress = ?, end_time = ?, disable_notification = ? WHERE id = ?";
        try (SqlStmt stmt = db.prepare(sql)) {
            stmt.setDouble(1, buildRequest.getProgress(false));
            stmt.setDateTime(2, buildRequest.getEndTime());
            stmt.setInt(3, buildRequest.getDisableNotification() ? 1 : 0);
            stmt.setInt(4, buildRequest.getID());
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    private static class DataBase extends BaseDataBase {
        public DataBase() {
            super();
        }
        public DataBase(Transaction trans) {
            super(trans);
        }
    }
}

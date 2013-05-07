package au.com.codeka.warworlds.server.ctrl;

import java.sql.Statement;

import au.com.codeka.common.model.Design;
import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.BuildRequest;
import au.com.codeka.warworlds.server.model.Star;

public class BuildQueueController {
    public void build(BuildRequest buildRequest) throws RequestException {
        try (Transaction t = DB.beginTransaction()) {
            //Star star = new StarController(t).getStar(buildRequest.getStarID());

            // TODO: check dependencies
            // TODO: check build limits

            if (buildRequest.getCount() > 5000) {
                buildRequest.setCount(5000);
            }

            SqlStmt stmt = t.prepare("INSERT INTO build_requests (star_id, planet_index, colony_id, empire_id," +
                                           " existing_building_id, design_kind, design_id," +
                                           " count, progress, start_time, end_time)" +
                                         " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    Statement.RETURN_GENERATED_KEYS);
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
            stmt.setInt(8, buildRequest.getCount());
            stmt.setDouble(9, buildRequest.getProgress(false));
            stmt.setDateTime(10, buildRequest.getStartTime());
            stmt.setDateTime(11, buildRequest.getEndTime());
            stmt.update();
            buildRequest.setID(stmt.getAutoGeneratedID());

            t.commit();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void stop(Star star, BuildRequest buildRequest) throws RequestException {
        star.getBuildRequests().remove(buildRequest);

        String sql = "DELETE FROM build_requests WHERE id = ?";
        try (SqlStmt stmt = DB.prepare(sql)) {
            stmt.setInt(1, buildRequest.getID());
            stmt.update();
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    public void accelerate(Star star, BuildRequest buildRequest, float accelerateAmount) throws RequestException {
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
        buildRequest.setProgress(buildRequest.getProgress(false) + progressToComplete);
    }
}

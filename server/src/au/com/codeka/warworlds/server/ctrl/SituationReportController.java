package au.com.codeka.warworlds.server.ctrl;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;

public class SituationReportController {
    private DataBase db;

    public SituationReportController() {
        db = new DataBase();
    }
    public SituationReportController(Transaction trans) {
        db = new DataBase(trans);
    }

    public void saveSituationReport(Messages.SituationReport sitrep_pb) throws RequestException {
        try {
            int sitrepID = db.saveSituationReport(sitrep_pb);

            Messages.SituationReport new_sitrep_pb = Messages.SituationReport.newBuilder(sitrep_pb)
                    .setKey(Integer.toString(sitrepID))
                    .build();

            int empireID = Integer.parseInt(new_sitrep_pb.getEmpireKey());
            String base64 = Base64.encodeBase64String(new_sitrep_pb.toByteArray());
            new NotificationController().sendNotificationToEmpire(empireID, "sitrep", base64);
        } catch (Exception e) {
            throw new RequestException(e);
        }
    }

    public List<Messages.SituationReport> fetch(Integer empireID, Integer starID, DateTime before,
                DateTime after, Messages.SituationReportFilter filter, int limit) throws RequestException {
        try {
            return db.fetch(empireID, starID, before, after, filter, limit);
        } catch(Exception e) {
            throw new RequestException(e);
        }
    }

    private static int getEventKinds(Messages.SituationReport sitrep) {
        int kinds = 0;
        if (sitrep.hasBuildCompleteRecord() && sitrep.getBuildCompleteRecord().getDesignId() != null) {
            kinds += 1 << Messages.SituationReportFilter.BuildCompleteAny_VALUE;
            if (sitrep.getBuildCompleteRecord().getBuildKind() == Messages.BuildRequest.BUILD_KIND.BUILDING) {
                kinds += 1 << Messages.SituationReportFilter.BuildCompleteBuilding_VALUE;
            } else {
                kinds += 1 << Messages.SituationReportFilter.BuildCompleteShips_VALUE;
            }
        }
        if (sitrep.hasMoveCompleteRecord() && sitrep.getMoveCompleteRecord().getFleetKey() != null) {
            kinds += 1 << Messages.SituationReportFilter.MoveComplete_VALUE;
        }
        if (sitrep.hasFleetUnderAttackRecord() && sitrep.getFleetUnderAttackRecord().getFleetKey() != null) {
            kinds += 1 << Messages.SituationReportFilter.FleetAttacked_VALUE;
        }
        if (sitrep.hasFleetDestroyedRecord() && sitrep.getFleetDestroyedRecord().getFleetDesignId() != null) {
            kinds += 1 << Messages.SituationReportFilter.FleetDestroyed_VALUE;
        }
        if (sitrep.hasFleetVictoriousRecord() && sitrep.getFleetVictoriousRecord().getFleetKey() != null) {
            kinds += 1 << Messages.SituationReportFilter.FleetVictorious_VALUE;
        }
        if (sitrep.hasColonyAttackedRecord() && sitrep.getColonyAttackedRecord().getColonyKey() != null) {
            kinds += 1 << Messages.SituationReportFilter.ColonyAttacked_VALUE;
        }
        if (sitrep.hasColonyDestroyedRecord() && sitrep.getColonyDestroyedRecord().getColonyKey() != null) {
            kinds += 1 << Messages.SituationReportFilter.ColonyDestroyed_VALUE;
        }

        return kinds;
    }

    private static class DataBase extends BaseDataBase {
        public DataBase() {
            super();
        }
        public DataBase(Transaction trans) {
            super(trans);
        }

        public int saveSituationReport(Messages.SituationReport sitrep_pb) throws Exception {
            String sql = "INSERT INTO situation_reports (empire_id, star_id, report_time, report, event_kinds)" +
                        " VALUES (?, ?, ?, ?, ?)";
            try (SqlStmt stmt = prepare(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, Integer.parseInt(sitrep_pb.getEmpireKey()));
                stmt.setInt(2, Integer.parseInt(sitrep_pb.getStarKey()));
                stmt.setDateTime(3, new DateTime(sitrep_pb.getReportTime() * 1000, DateTimeZone.UTC));
                stmt.setBytes(4, sitrep_pb.toByteArray());
                stmt.setInt(5, getEventKinds(sitrep_pb));
                stmt.update();

                return stmt.getAutoGeneratedID();
            }
        }

        public List<Messages.SituationReport> fetch(Integer empireID, Integer starID, DateTime before,
                DateTime after, Messages.SituationReportFilter filter, int limit) throws Exception {
            String sql = "SELECT report" +
                        " FROM situation_reports" +
                        " WHERE report_time < ?" +
                        (after == null ? "" : " AND report_time > ?") +
                        (empireID == null ? "" : " AND empire_id = ?") +
                        (starID == null ? "" : " AND star_id = ?") +
                        (filter == null || filter == Messages.SituationReportFilter.ShowAll ? "" : " AND event_kinds & ? > 0") +
                        " ORDER BY report_time DESC" +
                        " LIMIT "+limit;
            try (SqlStmt stmt = prepare(sql)) {
                stmt.setDateTime(1, before);
                int i = 2;
                if (after != null) {
                    stmt.setDateTime(i, after);
                    i++;
                }
                if (empireID != null) {
                    stmt.setInt(i, empireID);
                    i++;
                }
                if (starID != null) {
                    stmt.setInt(i, starID);
                    i++;
                }
                if (filter != null && filter != Messages.SituationReportFilter.ShowAll) {
                    stmt.setInt(i, 1 << filter.getNumber());
                    i++;
                }
                ResultSet rs = stmt.select();

                ArrayList<Messages.SituationReport> reports = new ArrayList<Messages.SituationReport>();
                while (rs.next()) {
                    reports.add(Messages.SituationReport.parseFrom(rs.getBytes(1)));
                }
                return reports;
            }
        }
    }
}

package au.com.codeka.warworlds.server.handlers;

import java.sql.Statement;

import org.joda.time.DateTime;

import au.com.codeka.common.protobuf.Messages;
import au.com.codeka.warworlds.server.RequestException;
import au.com.codeka.warworlds.server.RequestHandler;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlStmt;

public class DevicesHandler extends RequestHandler {

    @Override
    protected void post() throws RequestException {
        Messages.DeviceRegistration registration = getRequestBody(Messages.DeviceRegistration.class);

        Integer id = null;
        try(SqlStmt sql = DB.prepare(
                "SELECT id FROM devices WHERE device_id=? AND user_email=?")) {
            sql.setString(1, registration.getDeviceId());
            sql.setString(2, getCurrentUser());
            id = sql.selectFirstValue(Integer.class);
        } catch (Exception e) {
            throw new RequestException(500, e);
        }

        String stmt;
        if (id == null) {
            stmt = "INSERT INTO devices (device_id, user_email, device_model, device_manufacturer, device_build, device_version) VALUES (?, ?, ?, ?, ?, ?)";
        } else {
            stmt = "UPDATE devices SET device_id=?, user_email=?, device_model=?, device_manufacturer=?, device_build=?, device_version=? WHERE id=?";
        }

        try (SqlStmt sql = DB.prepare(stmt, Statement.RETURN_GENERATED_KEYS)) {
            sql.setString(1, registration.getDeviceId());
            sql.setString(2, getCurrentUser());
            sql.setString(3, registration.getDeviceModel());
            sql.setString(4, registration.getDeviceManufacturer());
            sql.setString(5, registration.getDeviceBuild());
            sql.setString(6, registration.getDeviceVersion());
            if (id != null) {
                sql.setInt(7, id);
            }
            sql.update();
            if (id == null) {
                id = sql.getAutoGeneratedID();
            }

            setResponseBody(Messages.DeviceRegistration.newBuilder()
                                    .setKey(id.toString())
                                    .build());
        } catch (Exception e) {
            throw new RequestException(500, e);
        }
    }

    @Override
    protected void put() throws RequestException {
        int id = Integer.parseInt(getUrlParameter("id"));

        String onlineStatusParameterValue = getRequest().getParameter("online_status");
        if (onlineStatusParameterValue != null && onlineStatusParameterValue.equals("1")) {
            Messages.DeviceOnlineStatus device_online_status_pb = getRequestBody(Messages.DeviceOnlineStatus.class);

            try (SqlStmt sql = DB.prepare("UPDATE devices SET online_since=? WHERE id=? AND user_email=?")) {
                if (device_online_status_pb.getIsOnline()) {
                    sql.setDateTime(1, DateTime.now());
                } else {
                    sql.setNull(1);
                }
                sql.setInt(2, id);
                sql.setString(3, getCurrentUser());
                sql.update();
            } catch (Exception e) {
                throw new RequestException(500, e);
            }
        } else {
            Messages.DeviceRegistration device_registration_pb = getRequestBody(Messages.DeviceRegistration.class);

            try (SqlStmt sql = DB.prepare("UPDATE devices SET gcm_registration_id=? WHERE id=? AND user_email=?")) {
                sql.setString(1, device_registration_pb.getGcmRegistrationId());
                sql.setInt(2, id);
                sql.setString(3, getCurrentUser());
                sql.update();
            } catch (Exception e) {
                throw new RequestException(500, e);
            }
        }
    }

    @Override
    protected void delete() throws RequestException {
        int id = Integer.parseInt(getUrlParameter("id"));

        try (SqlStmt sql = DB.prepare("DELETE FROM devices WHERE id=? and user_email=?")) {
            sql.setInt(1, id);
            sql.setString(2, getCurrentUser());
            sql.update();
        } catch (Exception e) {
            throw new RequestException(500, e);
        }
    }
}

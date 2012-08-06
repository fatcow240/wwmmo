"""empire.py: Empire-related tasks."""

from datetime import datetime, timedelta
import logging
import os

from google.appengine.ext import db

import webapp2 as webapp

import ctrl
from ctrl import empire as ctl
from ctrl import sector as sector_ctl
from model import empire as mdl
from model import c2dm
import protobufs.warworlds_pb2 as pb
import tasks


class BuildCheckPage(tasks.TaskPage):
  def get(self):
    """This page is called when a build operation is scheduled to finish.

    We need to confirm that the build is actually complete, set up the building or ship with the
    empire that built it, and then reschedule ourselves for the next build.
    """

    def _fetchOperationInTX(oper_key):
      """This is done in a transaction to make sure only one request processes the build."""

      oper_model = mdl.BuildOperation.get(oper_key)
      if not oper_model:
        return None

      # and now we're done with this operation
      oper_model.delete()
      return oper_model

    def _incrShipCountInTX(fleet_key):
      fleet_model = mdl.Fleet.get(fleet_key)
      if fleet_model.state == pb.Fleet.IDLE:
        fleet_model.numShips += 1
        fleet_model.put()
        return True
      return False

    complete_time = datetime.now() + timedelta(seconds=10)
    never_time = datetime(2000, 1, 1)

    # Fetch the keys outside of the transaction, cause we can't do that in a TX
    build_request_models = []
    query = (mdl.BuildOperation.all().filter("endTime <", complete_time)
                                     .filter("endTime >", never_time))
    for oper in query:
      build_request_model = db.run_in_transaction(_fetchOperationInTX, oper.key())
      if build_request_model:
        build_request_models.append(build_request_model)

    keys_to_clear = []
    for build_request_model in build_request_models:
      # OK, this build operation is complete (or will complete in the next 10 seconds -- close
      # enough) so we need to make sure the building/ship itself is added to the empire.
      colony_key = mdl.BuildOperation.colony.get_value_for_datastore(build_request_model)
      empire_key = mdl.BuildOperation.empire.get_value_for_datastore(build_request_model)

      logging.info("Build for empire \"%s\", colony \"%s\" complete." % (empire_key, colony_key))
      if build_request_model.designKind == pb.BuildRequest.BUILDING:
        model = mdl.Building(parent=build_request_model.key().parent())
        model.colony = colony_key
        model.empire = empire_key
        model.designName = build_request_model.designName
        model.buildTime = datetime.now()
        model.put()
        design = ctl.BuildingDesign.getDesign(model.designName)
      else:
        # if it's not a building, it must be a ship. We'll try to find a fleet that'll
        # work, but if we can't it's not a big deal -- just create a new one. Duplicates
        # don't hurt all that much (TODO: confirm)
        query = (mdl.Fleet.all().ancestor(build_request_model.key().parent())
                                .filter("empire", build_request_model.empire))
        done = False
        for fleet_model in query:
          if (fleet_model.designName == build_request_model.designName and
              fleet_model.state == pb.Fleet.IDLE):
            if db.run_in_transaction(_incrShipCountInTX, fleet_model.key()):
              done = True
              model = fleet_model
              # it's an existing fleet, so make sure we clear it's cached value
              keys_to_clear.append("fleet:%s" % str(fleet_model.key()))
              break
        if not done:
          model = mdl.Fleet(parent=build_request_model.key().parent())
          model.empire = build_request_model.empire
          model.designName = build_request_model.designName
          model.numShips = 1
          model.state = pb.Fleet.IDLE
          model.stateStartTime = datetime.now()
          model.put()
        design = ctl.ShipDesign.getDesign(model.designName)

      # Figure out the name of the star the object was built on, for the notification
      star_pb = sector_ctl.getStar(build_request_model.key().parent())

      # Send a notification to the player that construction of their building is complete
      msg = "Your %s on %s has been built." % (design.name, star_pb.name)
      logging.debug("Sending message to user [%s] indicating build complete." % (
          model.empire.user.email()))
      s = c2dm.Sender()
      devices = ctrl.getDevicesForUser(model.empire.user.email())
      for device in devices.registrations:
        s.sendMessage(device.device_registration_id, {"msg": msg})

      # clear the cached items that reference this building/fleet
      keys_to_clear.append("star:%s" % (star_pb.key))
      keys_to_clear.append("fleet:for-empire:%s" % (empire_key))
      keys_to_clear.append("colonies:for-empire:%s" % (empire_key))

    ctrl.clearCached(keys_to_clear)
    ctl.scheduleBuildCheck()

    self.response.write("Success!")


class StarSimulatePage(tasks.TaskPage):
  """Simulates all stars that have not been simulated for more than 18 hours.

  This is a scheduled task that runs every 6 hours. It looks for all stars that have not been
  simulated in more than 18 hours and simulates them now. By running every 6 hours, we ensure
  no star is more than 24 hours out of date."""
  def get(self):
    # find all colonies where last_simulation is at least 18 hours ago
    last_simulation = datetime.now() - timedelta(hours=18)

    star_keys = []
    for colony_mdl in mdl.Colony.all().filter("lastSimulation <", last_simulation):
      star_key = str(colony_mdl.key().parent())
      if star_key not in star_keys:
        star_keys.append(star_key)

    logging.debug("%d stars to simulate" % (len(star_keys)))
    for star_key in star_keys:
      star_pb = sector_ctl.getStar(star_key)
      ctl.simulate(star_pb)
      ctl.updateAfterSimulate(star_pb)

    self.response.write("Success!")

app = webapp.WSGIApplication([("/tasks/empire/build-check", BuildCheckPage),
                              ("/tasks/empire/star-simulate", StarSimulatePage)],
                             debug=os.environ["SERVER_SOFTWARE"].startswith("Development"))


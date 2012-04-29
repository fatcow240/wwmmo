package au.com.codeka.warworlds.game;

import java.util.List;

import org.joda.time.Period;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.model.BuildQueueManager;
import au.com.codeka.warworlds.model.BuildRequest;
import au.com.codeka.warworlds.model.BuildingDesign;
import au.com.codeka.warworlds.model.BuildingDesignManager;
import au.com.codeka.warworlds.model.Colony;

public class SolarSystemBuildQueueTab implements SolarSystemBuildDialog.Tab {
    private SolarSystemActivity mActivity;
    private BuildQueueListAdapter mBuildQueueListAdapter;
    private View mView;
    private Colony mColony;

    public SolarSystemBuildQueueTab(SolarSystemBuildDialog dialog, SolarSystemActivity activity) {
        mActivity = activity;
    }

    @Override
    public View getView() {
        if (mView == null)
            setup();
        return mView;
    }

    @Override
    public String getTitle() {
        return "Queued";
    }

    @Override
    public void setColony(Colony colony) {
        mColony = colony;

        if (mBuildQueueListAdapter != null && mColony != null) {
            mBuildQueueListAdapter.setBuildQueue(BuildQueueManager.getInstance().getBuildQueueForColony(mColony));
        }
    }

    private void setup() {
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        mView = inflater.inflate(R.layout.solarsystem_build_queue_tab, null);

        mBuildQueueListAdapter = new BuildQueueListAdapter();
        if (mColony != null) {
            mBuildQueueListAdapter.setBuildQueue(BuildQueueManager.getInstance().getBuildQueueForColony(mColony));
        }

        ListView buildQueueList = (ListView) mView.findViewById(R.id.build_queue);
        buildQueueList.setAdapter(mBuildQueueListAdapter);

        // make sure we're aware of any changes to the designs
        BuildingDesignManager.getInstance().addDesignsChangedListener(new BuildingDesignManager.DesignsChangedListener() {
            @Override
            public void onDesignsChanged() {
                if (mColony != null) {
                    mBuildQueueListAdapter.setBuildQueue(BuildQueueManager.getInstance().getBuildQueueForColony(mColony));
                }
            }
        });

        // make sure we're aware of changes to the build queue
        BuildQueueManager.getInstance().addBuildQueueUpdatedListener(new BuildQueueManager.BuildQueueUpdatedListener() {
            @Override
            public void onBuildQueueUpdated(List<BuildRequest> queue) {
                if (mColony != null) {
                    mBuildQueueListAdapter.setBuildQueue(BuildQueueManager.getInstance().getBuildQueueForColony(mColony));
                }
            }
        });
    }

    /**
     * This adapter is used to populate the list of buildings that are currently in progress.
     */
    private class BuildQueueListAdapter extends BaseAdapter {
        private List<BuildRequest> mQueue;

        public void setBuildQueue(List<BuildRequest> queue) {
            mQueue = queue;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            if (mQueue == null)
                return 0;
            return mQueue.size();
        }

        @Override
        public Object getItem(int position) {
            if (mQueue == null)
                return null;
            return mQueue.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService
                        (Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.solarsystem_buildings_design, null);
            }

            ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
            TextView row1 = (TextView) view.findViewById(R.id.building_row1);
            TextView row2 = (TextView) view.findViewById(R.id.building_row2);
            TextView row3 = (TextView) view.findViewById(R.id.building_row3);
            ProgressBar progress = (ProgressBar) view.findViewById(R.id.building_progress);

            BuildRequest request = mQueue.get(position);
            BuildingDesign design = request.getBuildingDesign();

            Bitmap bm = BuildingDesignManager.getInstance().getDesignIcon(design);
            if (bm != null) {
                icon.setImageBitmap(bm);
            } else {
                icon.setImageBitmap(null);
            }

            row1.setText(design.getName());
            Period remainingPeriod = request.getRemainingTime().toPeriod();
            row2.setText(String.format("%d %%, %d:%d left",
                    (int) request.getPercentComplete(),
                    remainingPeriod.getHours(), remainingPeriod.getMinutes()));

            row3.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            progress.setProgress((int) request.getPercentComplete());

            return view;
        }
    }
}

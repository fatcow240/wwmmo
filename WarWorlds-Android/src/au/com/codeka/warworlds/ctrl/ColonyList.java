package au.com.codeka.warworlds.ctrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.game.UniverseElementActivity;
import au.com.codeka.warworlds.model.Colony;
import au.com.codeka.warworlds.model.ImageManager;
import au.com.codeka.warworlds.model.PlanetImageManager;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarImageManager;

public class ColonyList extends FrameLayout {
    private UniverseElementActivity mActivity;
    private List<Colony> mColonies;
    private Colony mSelectedColony;
    private boolean mIsInitialized;
    private ColonyListAdapter mColonyListAdapter;

    public ColonyList(Context context, AttributeSet attrs) {
        super(context, attrs);

        View child = inflate(context, R.layout.colony_list_ctrl, null);
        this.addView(child);
    }

    public void refresh(UniverseElementActivity activity, List<Colony> colonies,
            Map<String, Star> stars) {
        mActivity = activity;
        mColonies = colonies;

        initialize();

        // if we had a colony selected, make sure we still have the same
        // colony selected after we refresh
        if (mSelectedColony != null) {
            Colony selectedColony = mSelectedColony;
            mSelectedColony = null;

            for (Colony c : mColonies) {
                if (c.getKey().equals(selectedColony.getKey())) {
                    mSelectedColony = c;
                    break;
                }
            }
        }

        mColonyListAdapter.setColonies(stars, colonies);
    }

    private void initialize() {
        if (mIsInitialized) {
            return;
        }
        mIsInitialized = true;

        mColonyListAdapter = new ColonyListAdapter();
        final ListView colonyList = (ListView) findViewById(R.id.colonies);
        colonyList.setAdapter(mColonyListAdapter);

        colonyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                mSelectedColony = mColonyListAdapter.getColonyAtPosition(position);
                mColonyListAdapter.notifyDataSetChanged();
                refreshStatistics();
            }
        });
    }

    private void refreshStatistics() {
        final TextView colonyInfo = (TextView) findViewById(R.id.colony_info);

        if (mSelectedColony == null) {
            colonyInfo.setText("");
        } else {
            String fmt = mActivity.getString(R.string.colony_overview_format);
            String html = String.format(fmt,
                    (int) mSelectedColony.getPopulation(),
                    mSelectedColony.getFarmingFocus(),
                    mSelectedColony.getMiningFocus(),
                    mSelectedColony.getConstructionFocus()
                );
            colonyInfo.setText(Html.fromHtml(html));
        }
    }

    /**
     * This adapter is used to populate the list of colonies that we're looking at.
     */
    private class ColonyListAdapter extends BaseAdapter {
        private ArrayList<Colony> mColonies;
        private Map<String, Star> mStars;
        private Map<String, Bitmap> mBitmaps;

        public ColonyListAdapter() {
            // whenever a new star/planet bitmap is generated, redraw the list
            StarImageManager.getInstance().addBitmapGeneratedListener(
                    new ImageManager.BitmapGeneratedListener() {
                @Override
                public void onBitmapGenerated(String key, Bitmap bmp) {
                    notifyDataSetChanged();
                }
            });
            PlanetImageManager.getInstance().addBitmapGeneratedListener(
                    new ImageManager.BitmapGeneratedListener() {
                @Override
                public void onBitmapGenerated(String key, Bitmap bmp) {
                    notifyDataSetChanged();
                }
            });

            mBitmaps = new HashMap<String, Bitmap>();
        }

        /**
         * Sets the list of fleets that we'll be displaying.
         */
        public void setColonies(Map<String, Star> stars, List<Colony> colonies) {
            mColonies = new ArrayList<Colony>(colonies);
            mStars = stars;

            Collections.sort(mColonies, new Comparator<Colony>() {
                @Override
                public int compare(Colony lhs, Colony rhs) {
                    // sort by star, then by planet index (that last part is TODO)
                    if (!lhs.getStarKey().equals(rhs.getStarKey())) {
                        Star lhsStar = mStars.get(lhs.getStarKey());
                        Star rhsStar = mStars.get(rhs.getStarKey());
                        return lhsStar.getName().compareTo(rhsStar.getName());
                    } else {
                        // TODO: yuck!
                        return lhs.getPlanetKey().compareTo(rhs.getPlanetKey());
                    }
                }
            });

            notifyDataSetChanged();
        }

        public Colony getColonyAtPosition(int position) {
            if (mColonies == null)
                return null;
            return mColonies.get(position);
        }

        @Override
        public int getCount() {
            if (mColonies == null)
                return 0;
            return mColonies.size();
        }

        @Override
        public Object getItem(int position) {
            return getColonyAtPosition(position);
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
                view = inflater.inflate(R.layout.colony_list_row, null);
            }

            Colony colony = mColonies.get(position);
            Star star = mStars.get(colony.getStarKey());

            ImageView starIcon = (ImageView) view.findViewById(R.id.star_icon);
            //ImageView planetIcon = (ImageView) view.findViewById(R.id.planet_icon);
            TextView colonyName = (TextView) view.findViewById(R.id.colony_name);
            TextView colonySummary = (TextView) view.findViewById(R.id.colony_summary);

            Bitmap bmp = mBitmaps.get(star.getKey());
            if (bmp == null) {
                int imageSize = (int)(star.getSize() * star.getStarType().getImageScale() * 2);
                bmp = StarImageManager.getInstance().getBitmap(mActivity, star, imageSize);
                if (bmp != null) {
                    mBitmaps.put(star.getKey(), bmp);
                }
            }
            starIcon.setImageBitmap(bmp);

            // TODO: planet icons will have to wait until we pass the Planet objects through
            //bmp = mBitmaps.get(colony.getPlanetKey());

            colonyName.setText(String.format("%s (TODO)", star.getName()));
            colonySummary.setText(String.format("Pop: %d", (int) colony.getPopulation()));

            if (mSelectedColony != null && mSelectedColony.getKey().equals(colony.getKey())) {
                view.setBackgroundColor(0xff0c6476);
            } else {
                view.setBackgroundColor(0xff000000);
            }

            return view;
        }
    }
}

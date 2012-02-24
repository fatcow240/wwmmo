package au.com.codeka.warworlds.game;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.model.ModelManager;
import au.com.codeka.warworlds.model.Planet;
import au.com.codeka.warworlds.model.Star;

/**
 * The \c StarfieldActivity is the "home" screen of the game, and displays the
 * starfield where you scroll around and interact with stars, etc.
 */
public class StarfieldActivity extends Activity {

    StarfieldSurfaceView mStarfield;
    TextView mUsername;
    TextView mMoney;
    TextView mStarName;
    ViewGroup mLoadingContainer;
    ViewGroup mPlanetIconsContainer;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // remove the title bar
    }

    @Override
    public void onResume() {
        super.onResume();

        setContentView(R.layout.starfield);

        mStarfield = (StarfieldSurfaceView) findViewById(R.id.starfield);
        mUsername = (TextView) findViewById(R.id.username);
        mMoney = (TextView) findViewById(R.id.money);
        mStarName = (TextView) findViewById(R.id.star_name);
        mLoadingContainer = (ViewGroup) findViewById(R.id.star_loading_container);
        mPlanetIconsContainer = (ViewGroup) findViewById(R.id.star_planet_icons_container);

        mPlanetIconsContainer.setVisibility(View.GONE);

        EmpireManager empire = EmpireManager.getInstance();
        mUsername.setText(empire.getDisplayName());
        mMoney.setText("$ 12,345"); // TODO: empire.getCash()
        mStarName.setText("");

        mStarfield.addStarSelectedListener(new StarfieldSurfaceView.OnStarSelectedListener() {
            @Override
            public void onStarSelected(Star star) {
                mStarName.setText(star.getName());

                // load the rest of the star's details as well
                mLoadingContainer.setVisibility(View.VISIBLE);
                mPlanetIconsContainer.setVisibility(View.GONE);

                ModelManager.requestStar(star.getSector().getX(), star.getSector().getY(),
                        star.getID(), new ModelManager.StarFetchedHandler() {
                    /**
                     * This is called on the main thread when the star is actually fetched.
                     */
                    @Override
                    public void onStarFetched(Star star) {
                        mLoadingContainer.setVisibility(View.GONE);
                        mPlanetIconsContainer.setVisibility(View.VISIBLE);

                        int numPlanetIcons = mPlanetIconsContainer.getChildCount();
                        for (int i = 0; i < numPlanetIcons; i++) {
                            ImageView icon = (ImageView) mPlanetIconsContainer.getChildAt(i);

                            if (i < star.getPlanets().length) {
                                icon.setVisibility(View.VISIBLE);

                                Planet planet = star.getPlanets()[i];
                                icon.setImageResource(planet.getPlanetType().getIconID());
                            } else {
                                icon.setVisibility(View.GONE);
                            }
                        }

                        // TODO: populate the rest of the view...
                    }
                });
            }
        });
    }
}

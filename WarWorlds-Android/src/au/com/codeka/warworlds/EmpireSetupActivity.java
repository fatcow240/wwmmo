package au.com.codeka.warworlds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import au.com.codeka.warworlds.api.ApiClient;
import au.com.codeka.warworlds.api.ApiException;
import au.com.codeka.warworlds.model.protobuf.Messages;

/**
 * This activity lets you set up your Empire before you actually join the game. You need
 * to give your Empire a name, race and what-not.
 */
public class EmpireSetupActivity extends BaseActivity {
    private static Logger log = LoggerFactory.getLogger(EmpireSetupActivity.class);
    private Context mContext = this;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE); // remove the title bar
    }

    @Override
    public void onResume() {
        super.onResume();

        setScreenContent();
    }

    /**
     * Sets up the contents of the home screen.
     */
    private void setScreenContent() {
        setContentView(R.layout.empire_setup);

        View rootView = findViewById(android.R.id.content);
        ActivityBackgroundGenerator.setBackground(rootView);

        final TextView empireName = (TextView) findViewById(R.id.empire_setup_name);
        final Button doneButton = (Button) findViewById(R.id.empire_setup_done);

        empireName.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                saveEmpire();
                return true;
            }
        });

        doneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                saveEmpire();
            }
        });

    }

    private void saveEmpire() {
        final TextView empireName = (TextView) findViewById(R.id.empire_setup_name);
        saveEmpire(empireName.getText().toString());
    }

    private void saveEmpire(final String empireName) {
        // if we've saved off the authentication cookie, cool!
        SharedPreferences prefs = Util.getSharedPreferences(mContext);
        final String accountName = prefs.getString("AccountName", null);
        if (accountName == null) {
            // TODO error!
            return;
        }

        final ProgressDialog pleaseWaitDialog = ProgressDialog.show(mContext, null, 
                "Please wait...", true);

        new AsyncTask<Void, Void, Boolean>() {
            private String mErrorMsg;

            @Override
            protected Boolean doInBackground(Void... arg0) {
                Messages.Empire empire = Messages.Empire.newBuilder().setDisplayName(empireName)
                        .setState(Messages.Empire.EmpireState.INITIAL)
                        .setEmail(accountName)
                        .build();

                try {
                    return ApiClient.putProtoBuf("empires", empire);
                } catch(ApiException e) {
                    log.error("An unexpected error occured!", e); // TODO??
                    mErrorMsg = e.getServerErrorMessage();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean wasSuccessful) {
                pleaseWaitDialog.dismiss();

                // say 'hello' again, to reset the empire details
                ServerGreeter.clearHello();

                if (!wasSuccessful) {
                    new StyledDialog.Builder(mContext)
                            .setTitle("Error")
                            .setMessage(mErrorMsg)
                            .setNeutralButton("OK", null)
                            .create().show();
                    return;
                }

                EmpireSetupActivity.this.setResult(RESULT_OK);
                EmpireSetupActivity.this.finish();
            }
        }.execute();

    }
}

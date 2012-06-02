package de.blau.android.prefs;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import de.blau.android.DialogFactory;
import de.blau.android.Main;
import de.blau.android.R;
import de.blau.android.prefs.AdvancedPrefDatabase.API;

/**
 * Simple class for Android's standard-Preference Activity
 * 
 * @author mb
 */
public class PrefEditor extends PreferenceActivity {
	
	private Resources r;
	private String KEY_PREFAPI;
	private String KEY_PREFLOGIN;
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		addPreferencesFromResource(R.xml.preferences);
		r = getResources();
		KEY_PREFAPI = r.getString(R.string.config_apibutton_key);
		KEY_PREFLOGIN = r.getString(R.string.config_loginbutton_key);
		fixUpPrefs();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	
		Preference apipref = getPreferenceScreen().findPreference(KEY_PREFAPI);
		Preference loginpref = getPreferenceScreen().findPreference(KEY_PREFLOGIN);
		AdvancedPrefDatabase db = new AdvancedPrefDatabase(this);
		API current = db.getCurrentAPI();
		if (current.id.equals(AdvancedPrefDatabase.ID_DEFAULT)) {
			apipref.setSummary(R.string.config_apibutton_summary);
			loginpref.setSummary(R.string.config_username_summary);
		} else {
			apipref.setSummary(current.name.isEmpty() ? current.url : current.name);
			loginpref.setSummary(null);
		}
	}
	
	private void fixUpPrefs() {
		Preference apipref = getPreferenceScreen().findPreference(KEY_PREFAPI);
		apipref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(PrefEditor.this, APIEditorActivity.class);
				if (Main.hasChanges()) {
					DialogFactory.createDataLossActivityDialog(PrefEditor.this, intent, -1).show();
				} else {
					startActivity(intent);
				}
				return true;
			}
		});
	}
	


}
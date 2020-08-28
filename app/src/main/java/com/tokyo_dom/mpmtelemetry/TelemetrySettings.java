package com.tokyo_dom.mpmtelemetry;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.Set;

public class TelemetrySettings extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            this.onBackPressed();
            return true;
            //finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        //    //Bluetooth
        private BluetoothAdapter myBluetooth = null;
        private Set<BluetoothDevice> pairedDevices;


        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            final ListPreference AddressList = (ListPreference) findPreference("address");
            final ListPreference LanguageList = (ListPreference) findPreference("language");

            // THIS IS REQUIRED IF YOU DON'T HAVE 'entries' and 'entryValues' in your XML
            setAddressListPreferenceData(AddressList);
            // Update list when user clicks on it
            AddressList.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    setAddressListPreferenceData(AddressList);
                    return false;
                }
            });
        }

        protected static void setAddressListPreferenceData(ListPreference lp) {
            //Bluetooth
            BluetoothAdapter myBluetooth = null;
            Set<BluetoothDevice> pairedDevices;

            //if the device has bluetooth
            myBluetooth = BluetoothAdapter.getDefaultAdapter();

            if(myBluetooth == null || !myBluetooth.isEnabled())
            {
                //Show a message that the device has no bluetooth adapter
                //Toast.makeText(getContext(), "Bluetooth Device Not Available", Toast.LENGTH_LONG).show();
                CharSequence[] btNames = {"None"};
                CharSequence[] btAddresses = {""};
                lp.setEntries(btNames);
                lp.setEntryValues(btAddresses);
                //lp.setDefaultValue("");
            }
            else
            {
                // Bluetooth is available, and enabled
                ArrayList entries = new ArrayList();
                ArrayList entryValues = new ArrayList();

                pairedDevices = myBluetooth.getBondedDevices();
                if (pairedDevices.size()>0)
                {
                    for(BluetoothDevice bt : pairedDevices)
                    {
                        entries.add(bt.getName() + "\n" + bt.getAddress()); //Get the device's name and the address
                        entryValues.add(bt.getAddress());
                        //entries[counter]=bt.getName();
                        //entryValues[counter]=bt.getAddress();
                    }
                    final CharSequence[] btNames = (CharSequence[]) entries.toArray(new CharSequence[entries.size()]);
                    final CharSequence[] btAddresses = (CharSequence[]) entryValues.toArray(new CharSequence[entryValues.size()]);
                    lp.setEntries(btNames);
                    lp.setEntryValues(btAddresses);
                }
                else
                {
                    //Toast.makeText(getContext(), "No Paired Bluetooth Devices Found.", Toast.LENGTH_LONG).show();
                    CharSequence[] btNames = {"None"};
                    CharSequence[] btAddresses = {""};
                    lp.setEntries(btNames);
                    lp.setEntryValues(btAddresses);
                    //lp.setDefaultValue("");
                }
            }
        }
    }
}
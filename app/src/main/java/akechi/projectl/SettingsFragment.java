package akechi.projectl;

import android.accounts.Account;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.List;

public class SettingsFragment
    extends Fragment
    implements View.OnClickListener
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        final LocalBroadcastManager lbMan= LocalBroadcastManager.getInstance(this.getActivity().getApplicationContext());
        {
            final IntentFilter ifilter= new IntentFilter(Event.AccountChange.ACTION);
            final BroadcastReceiver receiver= new BroadcastReceiver(){
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    SettingsFragment.this.loadSettings();
                }
            };
            lbMan.registerReceiver(receiver, ifilter);
            this.receivers.add(receiver);
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();

        final LocalBroadcastManager lbMan= LocalBroadcastManager.getInstance(this.getActivity().getApplicationContext());
        for(final BroadcastReceiver receiver : this.receivers)
        {
            lbMan.unregisterReceiver(receiver);
        }
        this.receivers.clear();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View v= inflater.inflate(R.layout.fragment_settings, container, false);

        this.roomIdView= (EditText)v.findViewById(R.id.roomIdText);
        this.saveButton= (Button)v.findViewById(R.id.saveButton);
        this.saveButton.setOnClickListener(this);

        this.loadSettings();

        return v;
    }

    @Override
    public void onClick(View v)
    {
        final AppContext appContext= (AppContext)this.getActivity().getApplicationContext();
        final Account account= appContext.getAccount();

        {
            final String ids= this.roomIdView.getText().toString();
            final Iterable<String> roomIds= Iterables.filter(
                    Splitter.on(System.getProperty("line.separator")).split(ids),
                    new Predicate<String>() {
                        @Override
                        public boolean apply(String input) {
                            return !Strings.isNullOrEmpty(input);
                        }
                    }
            );
            appContext.setRoomIds(account, roomIds);
        }

        Toast.makeText(this.getActivity(), "Saved", Toast.LENGTH_SHORT).show();

        // notify others for preference changed
        {
            final LocalBroadcastManager lbMan= LocalBroadcastManager.getInstance(this.getActivity().getApplicationContext());
            final Intent intent= new Intent(Event.PreferenceChange.ACTION);
            lbMan.sendBroadcast(intent);
        }
    }

    private void loadSettings()
    {
        final AppContext appContext= (AppContext)this.getActivity().getApplicationContext();
        final Account account= appContext.getAccount();
        if(account != null)
        {
            final Iterable<String> roomIds= appContext.getRoomIds(account);
            this.roomIdView.setText(Joiner.on(System.getProperty("line.separator")).join(roomIds));
        }
    }

    private EditText roomIdView;
    private Button saveButton;

    private final List<BroadcastReceiver> receivers= Lists.newLinkedList();
}

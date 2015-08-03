package akechi.projectl;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.common.base.Supplier;

import java.io.EOFException;
import java.io.IOException;

import akechi.projectl.async.LingrTaskLoader;
import jp.michikusa.chitose.lingr.LingrClient;
import jp.michikusa.chitose.lingr.LingrClientFactory;
import jp.michikusa.chitose.lingr.LingrException;
import jp.michikusa.chitose.lingr.Room;

public class SayFragment
    extends Fragment
    implements Button.OnClickListener, RoomListFragment.OnRoomSelectedListener, LoaderManager.LoaderCallbacks<Room.Message>
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View v= inflater.inflate(R.layout.fragment_say, container, false);

        this.inputText= (EditText) v.findViewById(R.id.messageView);
        this.sayButton= (Button) v.findViewById(R.id.sayButton);

        this.sayButton.setOnClickListener(this);

        this.getLoaderManager().initLoader(0, null, this);

        return v;
    }

    @Override
    public void onClick(View v)
    {
        this.sayButton.setEnabled(false);
        this.getLoaderManager().getLoader(0).forceLoad();
    }

    @Override
    public void onRoomSelected(CharSequence roomId)
    {
    }

    @Override
    public Loader<Room.Message> onCreateLoader(int id, Bundle args)
    {
        return new MessagePostLoader(this.getActivity(), new Supplier<String>(){
            @Override
            public String get()
            {
                return inputText.getText().toString();
            }
        });
    }

    @Override
    public void onLoadFinished(Loader<Room.Message> loader, Room.Message data)
    {
        if(data == null)
        {
            return;
        }
        this.inputText.setText("");
        // close keypad
        final InputMethodManager imeManager= (InputMethodManager)this.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imeManager.hideSoftInputFromWindow(this.inputText.getWindowToken(), 0);
        this.sayButton.setEnabled(true);
        Toast.makeText(this.getActivity(), "Posted", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoaderReset(Loader<Room.Message> loader)
    {
    }

    private static final class MessagePostLoader
        extends LingrTaskLoader<Room.Message>
    {
        public MessagePostLoader(Context context, Supplier<String> textSupplier)
        {
            super(context);
            this.textSupplier= textSupplier;
        }

        @Override
        public Room.Message loadInBackground(CharSequence authToken, LingrClient lingr)
            throws IOException, LingrException
        {
            final String text= this.textSupplier.get();
            if(Strings.isNullOrEmpty(text))
            {
                return null;
            }
            final AppContext appContext= this.getApplicationContext();
            final Account account= appContext.getAccount();
            final String roomId= appContext.getRoomId(account);

            return lingr.say(authToken, roomId, text);
        }

        private final Supplier<String> textSupplier;
    }

    private static final LingrClientFactory lingrFactory= LingrClientFactory.newLingrClientFactory(AndroidHttp.newCompatibleTransport());

    private EditText inputText;
    private Button sayButton;
}

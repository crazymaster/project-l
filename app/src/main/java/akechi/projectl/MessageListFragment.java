package akechi.projectl;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.io.EOFException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import jp.michikusa.chitose.lingr.Archive;
import jp.michikusa.chitose.lingr.LingrClient;
import jp.michikusa.chitose.lingr.LingrClientFactory;
import jp.michikusa.chitose.lingr.Room;
import jp.michikusa.chitose.lingr.Room.Message;

public class MessageListFragment
    extends Fragment
    implements SwipeRefreshLayout.OnRefreshListener, LoaderManager.LoaderCallbacks<Iterable<Message>>, RoomListFragment.OnRoomSelectedListener
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        this.getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        final View view=  inflater.inflate(R.layout.fragment_message_list, container, false);

        this.swipeRefreshLayout = (SwipeRefreshLayout)view.findViewById(R.id.scrollView);
        this.messageView = (ListView)view.findViewById(R.id.messageListView);

        this.swipeRefreshLayout.setOnRefreshListener(this);
        this.messageView.setAdapter(new MessageAdapter(this.getActivity(), Collections.<Message>emptyList()));

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onRoomSelected(CharSequence roomId)
    {
        Log.i("MessageListFragment", "On room selected " + roomId);
        if(!roomId.toString().equals(this.roomId))
        {
            final MessageAdapter adapter= (MessageAdapter)this.messageView.getAdapter();
            adapter.clear();
        }
        this.roomId= roomId.toString();

        this.swipeRefreshLayout.setRefreshing(true);
        this.getLoaderManager().getLoader(0).forceLoad();
    }

    @Override
    public void onRefresh()
    {
        final MessageListFragment that= this;
        final AccountManager manager= AccountManager.get(this.getActivity());
        final Message oldestMessage= (Message)this.messageView.getAdapter().getItem(0);
        new AsyncTask<String, Void, List<Message>>(){
            @Override
            protected List<Message> doInBackground(String... params)
            {
                final String id= params[0];
                final String roomId= that.roomId;
                final Account[] accounts= manager.getAccountsByType("com.lingr");
                if(accounts.length <= 0)
                {
                    this.message= "First, you have to create lingr account";
                    return Collections.emptyList();
                }
                final Account account= accounts[0];
                try
                {
                    final LingrClient lingr= lingrFactory.newLingrClient();
                    String authToken= manager.blockingGetAuthToken(account, "", true);
                    if(!lingr.verifySession(authToken))
                    {
                        manager.invalidateAuthToken("com.lingr", authToken);
                        authToken= manager.blockingGetAuthToken(account, "", true);
                    }
                    final Archive archive= lingr.getArchive(authToken, roomId, id, 100);
                    return archive.getMessages();
                }
                catch (Exception e)
                {
                    Log.e("MessageListFragment", "Couldn't load archives from " + roomId, e);
                    this.message= e.toString();
                    return Collections.emptyList();
                }
            }

            @Override
            protected void onPostExecute(List<Message> messages)
            {
                final MessageAdapter adapter= (MessageAdapter)that.messageView.getAdapter();
                adapter.insertHead(messages);
                adapter.notifyDataSetChanged();
                that.swipeRefreshLayout.setRefreshing(false);
            }

            private String message;
        }.execute(oldestMessage.getId());
    }

    @Override
    public void onLoadFinished(Loader<Iterable<Message>> loader, Iterable<Message> data)
    {
        Log.i("MessageListFragment", String.format("got %d messages", Iterables.size(data)));

        final MessageAdapter adapter= (MessageAdapter)this.messageView.getAdapter();
        for (final Message m : data) {
            adapter.add(m);
        }
        ((MessageAdapter)this.messageView.getAdapter()).notifyDataSetChanged();
        this.messageView.setSelection(this.messageView.getAdapter().getCount() - 1);
        this.swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public Loader<Iterable<Message>> onCreateLoader(int id, Bundle args)
    {
        final MessageListFragment that= this;
        final AccountManager manager= AccountManager.get(this.getActivity());
        final Loader<Iterable<Message>> loader= new AsyncTaskLoader<Iterable<Message>>(this.getActivity()){
            @Override
            public Iterable<Message> loadInBackground()
            {
                final String roomId= that.roomId;
                final Account[] accounts= manager.getAccountsByType("com.lingr");
                if(accounts.length <= 0)
                {
                    Log.i("MessageListFragment", "There's no account, return empty list immediately");
                    return Collections.<Message>emptyList();
                }
                // TODO choose account
                final Account account= accounts[0];
                try
                {
                    String authToken= manager.blockingGetAuthToken(account, "", true);
                    final LingrClient lingr= lingrFactory.newLingrClient();
                    if(!lingr.verifySession(authToken))
                    {
                        Log.i("MessageListFragment", "authToken " + authToken + " is expired");
                        manager.invalidateAuthToken("com.lingr", authToken);
                        authToken= manager.blockingGetAuthToken(account, "", true);
                    }

                    final Room room= lingr.showRoom(authToken, roomId);
                    final Room.RoomInfo info= Iterables.find(room.getRooms(), new Predicate<Room.RoomInfo>(){
                        @Override
                        public boolean apply(Room.RoomInfo input)
                        {
                            return roomId.equals(input.getId());
                        }
                    });
                    return info.getMessages();
                }
                catch(EOFException e)
                {
                    Log.e("MessageListFragment", "Couldn't enter a room " + roomId, e);
                    final Handler handler= new Handler(Looper.getMainLooper());
                    final Context ctx= this.getContext();
                    handler.post(new Runnable(){
                        @Override
                        public void run()
                        {
                            Toast.makeText(ctx, "Unexpectedly EOFException detected", Toast.LENGTH_LONG).show();
                        }
                    });
                    return Collections.<Message>emptyList();
                }
                catch(Exception e)
                {
                    Log.e("MessageListFragment", "Couldn't enter a room " + roomId, e);
                    return Collections.<Message>emptyList();
                }
            }
        };
        return loader;
    }

    @Override
    public void onLoaderReset(Loader<Iterable<Message>> loader)
    {
        Log.i("MessageListFragment", "On reset");
    }

    public static final class MessageAdapter
        extends BaseAdapter
    {
        public MessageAdapter(Context context, List<? extends Message> messages)
        {
            this.context = context;
            this.messages.addAll(messages);
        }

        public void insertHead(Collection<? extends Message> messages)
        {
            this.messages.addAll(0, messages);
        }

        public void add(Message e)
        {
            this.messages.add(e);
        }

        public void clear()
        {
            this.messages.clear();
        }

        @Override
        public int getCount()
        {
            return this.messages.size();
        }

        @Override
        public Message getItem(int position)
        {
            return this.messages.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            final View view;
            if(convertView == null)
            {
                final LayoutInflater inflater= (LayoutInflater)this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view= inflater.inflate(R.layout.custom_message, null);
            }
            else
            {
                view= convertView;
            }

            final Message data= this.getItem(position);
            final ImageView iconView= (ImageView)view.findViewById(R.id.iconView);
            iconView.setImageURI(Uri.parse(data.getIconUrl()));

            final TextView nicknameView= (TextView)view.findViewById(R.id.nicknameView);
            nicknameView.setText(data.getNickname());

            final TextView textView= (TextView)view.findViewById(R.id.textView);
            textView.setText(data.getText());

            return view;
        }

        private final Context context;

        private final List<Message> messages = new LinkedList<>();
    }

    private static final LingrClientFactory lingrFactory= LingrClientFactory.newLingrClientFactory(AndroidHttp.newCompatibleTransport());

    private SwipeRefreshLayout swipeRefreshLayout;

    private ListView messageView;

    private String roomId;
}

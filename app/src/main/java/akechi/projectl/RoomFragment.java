package akechi.projectl;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import jp.michikusa.chitose.lingr.Room;

public class RoomFragment
    extends Fragment
    implements RoomListFragment.OnRoomSelectedListener
{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v= inflater.inflate(R.layout.fragment_room, container, false);

        this.messageListFragment= (MessageListFragment)this.getChildFragmentManager().findFragmentByTag("messageList");
        this.sayFragment= (SayFragment)this.getChildFragmentManager().findFragmentByTag("say");

        return v;
    }

    @Override
    public void onRoomSelected(CharSequence roomId)
    {
        this.messageListFragment.onRoomSelected(roomId);
        this.sayFragment.onRoomSelected(roomId);
    }

    private MessageListFragment messageListFragment;
    private SayFragment sayFragment;
}
package com.example.jonathan.inventoryassistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;

public class GroupList extends Activity {

    private static final String PATH = "/database-action-wear";
    private static final String ACTION_KEY = "action-key";
    private static final String DELETE_GROUP_KEY = "delete-group-key";
    private static final String GROUP_NAME_KEY = "group-name";

    private static final String RENAME_GROUP_KEY = "rename-group-key";
    private static final String NEW_GROUP_NAME_KEY = "new-group-name";

    private static final String UPDATE_GROUP_LIST = "com.example.jonathan.inventoryassistant.update-group-list";

    GoogleApiClient mGoogleApiClient;

    GroupReaderDbHelper groupReaderDbHelper;
    ItemReaderDbHelper itemReaderDbHelper;
    ArrayList<String> groupArray;

    ReceiveMessages myReceiver = null;
    Boolean myReceiverIsRegistered = false;

    ListView groupList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);

        getActionBar().setDisplayShowHomeEnabled(true);
        getActionBar().setDisplayHomeAsUpEnabled(false);
        getActionBar().setLogo(R.drawable.action_bar_logo);
        getActionBar().setDisplayUseLogoEnabled(true);
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        upArrow.setColorFilter(getResources().getColor(R.color.backArrow), PorterDuff.Mode.SRC_ATOP);
        getActionBar().setHomeAsUpIndicator(upArrow);

        groupReaderDbHelper = new GroupReaderDbHelper(this);
        itemReaderDbHelper = new ItemReaderDbHelper(this);

        setTitle("i-Nventory");
        myReceiver = new ReceiveMessages();


        Intent intent = new Intent(this, MobileListenerService.class);
        startService(intent);

        showCustomToast("Long press entries for options");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        // Now you can use the Data Layer API
                    }
                    @Override
                    public void onConnectionSuspended(int cause) {
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                    }
                })
                        // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();

        makeGroupList();

        View footerView =  ((LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.group_list_footer, null, false);
        groupList.addFooterView(footerView);
    }

    @Override
    public void onResume()
    {  // After a pause OR at startup
        super.onResume();
        if (!myReceiverIsRegistered) {
            registerReceiver(myReceiver, new IntentFilter(UPDATE_GROUP_LIST));
            myReceiverIsRegistered = true;
        }
        groupReaderDbHelper = new GroupReaderDbHelper(this);
        makeGroupList();
        Log.d("onResume", "ALL GROUPS IS RESUMING");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (myReceiverIsRegistered) {
            unregisterReceiver(myReceiver);
            myReceiverIsRegistered = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_help:
                Intent intent = new Intent(this, HelpScreen.class);
                this.startActivity(intent);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.groupList) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
            menu.setHeaderTitle("Group: " + groupArray.get(info.position));
            String[] menuItems = {"Rename", "Delete", "Add/rewrite NFC tag for whole group"};
            for (int i = 0; i < menuItems.length; i++) {
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int menuItemIndex = item.getItemId();
        String[] menuItems = {"Rename", "Delete", "Add/rewrite NFC tag for whole group"};
        String optionSelected = menuItems[menuItemIndex];
        String entrySelected = groupArray.get(info.position);
        switch (optionSelected) {
            case "Rename":
                renameGroupDialog(entrySelected);
                break;
            case "Delete":
                groupReaderDbHelper.deleteGroup(entrySelected);
                itemReaderDbHelper.deleteItemsInGroup(entrySelected);
                sendDeleteGroupToWear(entrySelected);
                makeGroupList();
                break;
            case "Add/rewrite NFC tag for whole group":
                makeGroupTag(entrySelected);
                break;
            default:
                return true;
        }
        return true;
    }

    public void makeGroupTag(String groupName) {
        Intent i = new Intent();
        i.setClass(this, WriteNfcTag.class);
        i.putExtra("groupName", groupName);
        i.putExtra("textToWrite", groupName);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    public boolean renameGroupDialog(final String oldName) {
        AlertDialog.Builder alert = new AlertDialog.Builder(GroupList.this);
        alert.setTitle("Enter a new name:");

        final EditText input = new EditText(GroupList.this);
        input.setText(oldName);
        alert.setView(input);

        alert.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newName = input.getEditableText().toString();
                groupReaderDbHelper.renameGroup(oldName, newName);
                itemReaderDbHelper.renameGroup(oldName, newName);
                sendRenameGroupToWear(oldName, newName);
                makeGroupList();
            }
        });

        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = alert.create();
        alertDialog.show();
        return false;
    }

    private void sendRenameGroupToWear(String oldGroupName, String newGroupName) {
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(PATH);
        putDataMapReq.getDataMap().putString(ACTION_KEY, RENAME_GROUP_KEY);
        putDataMapReq.getDataMap().putString(GROUP_NAME_KEY, oldGroupName);
        putDataMapReq.getDataMap().putString(NEW_GROUP_NAME_KEY, newGroupName);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_group_list, menu);
        return true;
    }

    public void makeNewGroup(View view) {
        Intent i = new Intent();
        i.setClass(this, NewGroup.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    public void showItemList(String groupName) {
        Intent i = new Intent();
        i.putExtra("groupName", groupName);
        i.setClass(this, ItemList.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    private void sendDeleteGroupToWear(String groupName) {
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create(PATH);
        putDataMapReq.getDataMap().putString(ACTION_KEY, DELETE_GROUP_KEY);
        putDataMapReq.getDataMap().putString(GROUP_NAME_KEY, groupName);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
    }

    private void makeGroupList() {
        Cursor cursor = groupReaderDbHelper.getAllGroups();
        cursor.moveToPosition(-1);

        groupList = (ListView) findViewById(R.id.groupList);
        groupArray = new ArrayList<>();

        while (cursor.moveToNext()) {
            String groupName = cursor.getString(cursor.getColumnIndexOrThrow(GroupReaderContract.GroupEntry.GROUP_NAME));
            groupArray.add(groupName);
            Log.d("GrpLst", "Trying to print out all the items in the GroupList");
        }
        cursor.close();
        if (groupArray.size() == 0) {
            //groupArray.add("(no groups)");
            ArrayAdapter<String> arrayAdapter =
                    new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, groupArray);
            groupList.setAdapter(arrayAdapter);
            groupList.setOnItemClickListener(null);
        } else {
            ArrayAdapter<String> arrayAdapter =
                    new ArrayAdapter<>(this,android.R.layout.simple_list_item_1, groupArray);
            groupList.setAdapter(arrayAdapter);

            // register onClickListener to handle click events on each item
            groupList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                // argument position gives the index of item which is clicked
                public void onItemClick(AdapterView<?> arg0, View v, int position, long arg3) {
                    showCustomToast("Long press entries for options");
                    String selectedGroup = groupArray.get(position);
                    showItemList(selectedGroup);
                }
            });

//            groupList.setLongClickable(true);
//            groupList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
//                public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int pos, long id) {
//                    deleteEntryDialog(groupArray.get(pos));
//                    return true;
//                }
//            });

            registerForContextMenu(groupList);
        }
    }

    public void showCustomToast(String text) {
        final Toast t = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
        t.show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                t.cancel();
            }
        }, 1000);
    }

    public class ReceiveMessages extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(UPDATE_GROUP_LIST)) {
                makeGroupList();
            }
        }
    }
}

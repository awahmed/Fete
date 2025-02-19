package com.example.arturolopez.fete;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private Button chatButton;
    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    public static final int RC_SIGN_IN = 1;     ///request code
    private static final int RC_PHOTO_PICKER = 2;



    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername, uid;
    private String partyid;

    //Firebase instance variables
    private FirebaseDatabase mFirebaseDatabase;             //entry point for our app to access the database
    private DatabaseReference mUserRef, mspecificUserRef;
    private DatabaseReference mMessagesDatabaseReference;   //references a specific part of the database
    private ChildEventListener mChildEventListener;
    private DatabaseReference mPartyRef;

    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth mAuth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        TextView toolbarText = findViewById(R.id.toolbar_text);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if(toolbarText!=null && toolbar!=null) {
            toolbarText.setText(R.string.messages);
            setSupportActionBar(toolbar);
        }

        mUsername = ANONYMOUS;

        //initialize Firebase components
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("Chat");
        mPartyRef = mFirebaseDatabase.getReference().child("parties");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);


        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if(user != null){
            uid = user.getUid();
            Log.d(TAG, "uid " + uid);
            mUserRef = mFirebaseDatabase.getReference().child("users");
            mspecificUserRef = mUserRef.child(uid);
            Log.d(TAG,"uid: " + uid);
            mspecificUserRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if(dataSnapshot.child("username").getValue() != null){
                        mUsername = dataSnapshot.child("username").getValue().toString();
                        Log.d(TAG, "username " + mUsername);
                    }
                    else{
                        mUsername = "no name";
                        Log.d(TAG, "no username");
                    }
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }


        // Initialize message ListView and its adapter
        List<Message> Messages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, Messages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    // Enable Send button when there's text to send
                    // to avoid sending plain text in the chat
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(true);
                }
            }
            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                Message friendlyMessage = new Message(mMessageEditText.getText().toString(), mUsername);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });


        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null){
                    //user is signed in

                }
                else {
                    //user is signed out
                    startActivityForResult(
                            AuthUI.getInstance().createSignInIntentBuilder().setAvailableProviders(Arrays.asList(
                                    new AuthUI.IdpConfig.EmailBuilder().build(),
                                    new AuthUI.IdpConfig.GoogleBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
        attachDatabaseReadListener();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                //signed in succeeded, set up UI
            } else if(requestCode == RESULT_CANCELED){
                //sign in was cancelled, finish activity
                finish();
            }
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        //ad listener
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
    }


    @Override
    protected void onPause(){
        super.onPause();
        //remove listener
        if(mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        mMessageAdapter.clear();
    }



    private void attachDatabaseReadListener(){
        if(mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override //called when new message is inserted in the messages list
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    Message friendlyMessage = dataSnapshot.getValue(Message.class);
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override   //contents of existing message gets changed
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                }

                @Override   //existing message is deleted
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                }

                @Override      //if message changes position
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                }

                @Override   //error occurred (usually when you dont have permission
                public void onCancelled(DatabaseError databaseError) {
                }
            };
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener(){
        if(mChildEventListener != null){
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }
}

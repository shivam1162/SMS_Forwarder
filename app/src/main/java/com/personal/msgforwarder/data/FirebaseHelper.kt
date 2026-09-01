package com.personal.msgforwarder.data

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Singleton handling all Firebase Realtime Database operations.
 * All paths live under channels/<pairingCode>/.
 *
 * Database structure:
 *   channels/
 *     <pairingCode>/
 *       active: Boolean
 *       devices/
 *         sender: <fcmToken>
 *         receiver: <fcmToken>
 *       heartbeat/
 *         lastSeen: Long (timestamp)
 *       messages/
 *         <auto-id>/
 *           sender: String
 *           body: String
 *           timestamp: Long
 */
object FirebaseHelper {

    private val database = FirebaseDatabase.getInstance()

    private fun channelRef(code: String) = database.getReference("channels").child(code)

    // --- Activation ---

    fun setActive(code: String, active: Boolean) {
        channelRef(code).child("active").setValue(active)
    }

    fun listenForActivation(code: String, callback: (Boolean) -> Unit): ValueEventListener {
        val ref = channelRef(code).child("active")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val active = snapshot.getValue(Boolean::class.java) ?: false
                callback(active)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeActivationListener(code: String, listener: ValueEventListener) {
        channelRef(code).child("active").removeEventListener(listener)
    }

    // --- Messages ---

    fun pushMessage(code: String, sender: String, body: String, timestamp: Long) {
        val message = MessageData(sender = sender, body = body, timestamp = timestamp)
        channelRef(code).child("messages").push().setValue(message)
    }

    fun listenForMessages(code: String, callback: (MessageData) -> Unit): ChildEventListener {
        val ref = channelRef(code).child("messages").orderByChild("timestamp")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(MessageData::class.java)
                if (message != null) {
                    callback(message)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addChildEventListener(listener)
        return listener
    }

    fun removeMessagesListener(code: String, listener: ChildEventListener) {
        channelRef(code).child("messages").orderByChild("timestamp").removeEventListener(listener)
    }

    /**
     * Deletes all messages older than maxAgeMillis (default: 30 minutes) from Firebase.
     */
    fun purgeOldMessages(code: String, maxAgeMillis: Long = 30 * 60 * 1000L) {
        val cutoffTimestamp = System.currentTimeMillis() - maxAgeMillis
        val oldMessagesQuery = channelRef(code).child("messages")
            .orderByChild("timestamp")
            .endAt(cutoffTimestamp.toDouble())

        oldMessagesQuery.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    child.ref.removeValue()
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- Device Tokens ---

    fun writeDeviceToken(code: String, role: String, token: String) {
        channelRef(code).child("devices").child(role).setValue(token)
    }

    fun getPartnerToken(code: String, myRole: String, callback: (String?) -> Unit) {
        val partnerRole = if (myRole == "sender") "receiver" else "sender"
        channelRef(code).child("devices").child(partnerRole)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    callback(snapshot.getValue(String::class.java))
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null)
                }
            })
    }

    // --- Heartbeat ---

    fun writeHeartbeat(code: String, timestamp: Long) {
        channelRef(code).child("heartbeat").child("lastSeen").setValue(timestamp)
    }

    fun listenForHeartbeat(code: String, callback: (Long) -> Unit): ValueEventListener {
        val ref = channelRef(code).child("heartbeat").child("lastSeen")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastSeen = snapshot.getValue(Long::class.java) ?: 0L
                callback(lastSeen)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removeHeartbeatListener(code: String, listener: ValueEventListener) {
        channelRef(code).child("heartbeat").child("lastSeen").removeEventListener(listener)
    }
}

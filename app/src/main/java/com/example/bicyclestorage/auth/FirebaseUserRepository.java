package com.example.bicyclestorage.auth;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseUserRepository {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public String getUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public String getEmail() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;
    }

    public Task<Void> createUserProfile(@NonNull String uid, @NonNull String email, @NonNull String username) {
        Map<String,Object> data = new HashMap<>();
        data.put("email", email);
        data.put("username", username);
        data.put("createdAt", System.currentTimeMillis());
        return db.collection("users").document(uid).set(data);
    }

    public DocumentReference userDoc() {
        String uid = getUid();
        if (uid == null) return null;
        return db.collection("users").document(uid);
    }

    public Task<Void> updateUsername(String newUsername) {
        DocumentReference doc = userDoc();
        if (doc == null) return null;
        Map<String,Object> upd = new HashMap<>();
        upd.put("username", newUsername);
        return doc.update(upd);
    }

    public void logout() {
        auth.signOut();
    }

    public FirebaseAuth getAuth() {
        return auth;
    }
}
package com.example.bicyclestorage.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bicyclestorage.R;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AccountActivity extends AppCompatActivity {

    private final FirebaseUserRepository repo = new FirebaseUserRepository();
    private TextView emailLabel;
    private EditText usernameField;
    private Button updateButton, logoutButton, passwordResetButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!repo.isLoggedIn()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_account);

        emailLabel = findViewById(R.id.emailLabel);
        usernameField = findViewById(R.id.usernameField);
        updateButton = findViewById(R.id.updateButton);
        logoutButton = findViewById(R.id.logoutButton);
        passwordResetButton = findViewById(R.id.passwordResetButton);

        emailLabel.setText("Email: " + repo.getEmail());

        if (repo.userDoc() != null) {
            repo.userDoc().get()
                    .addOnSuccessListener(this::fillUser)
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Load error: " + readable(e.getMessage()),
                                    Toast.LENGTH_LONG).show());
        }

        updateButton.setOnClickListener(v -> updateUsername());
        logoutButton.setOnClickListener(v -> {
            repo.logout();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        // Local password change (reauth + updatePassword)
        passwordResetButton.setOnClickListener(v -> startChangePasswordDialog());
    }

    private void fillUser(DocumentSnapshot snap) {
        if (snap != null && snap.exists()) {
            String uname = snap.getString("username");
            if (uname != null) {
                usernameField.setText(uname);
            } else {
                usernameField.setHint("No username");
            }
        } else {
            // Document does NOT exist → create a default profile
            FirebaseAuth a = FirebaseAuth.getInstance();
            if (a.getCurrentUser() == null) return;
            String uid = a.getCurrentUser().getUid();
            String email = a.getCurrentUser().getEmail();
            Map<String, Object> data = new HashMap<>();
            data.put("username", deriveDefaultUsername(email));
            data.put("email", email);
            data.put("createdAt", System.currentTimeMillis());

            FirebaseFirestore.getInstance().collection("users")
                    .document(uid)
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        usernameField.setText((String) data.get("username"));
                        Toast.makeText(this, "Default profile created.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed to create default profile: " + readable(e.getMessage()),
                                    Toast.LENGTH_LONG).show());
        }
    }

    private String deriveDefaultUsername(String email) {
        if (email == null) return "user";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private void updateUsername() {
        String newU = usernameField.getText().toString().trim();
        if (newU.isEmpty()) {
            Toast.makeText(this, "Username cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.updateUsername(newU)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Updated.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + readable(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    // Local password change (current password + new password)
    private void startChangePasswordDialog() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You are not signed in.", Toast.LENGTH_LONG).show();
            return;
        }
        String email = user.getEmail();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "No email address associated with this account.", Toast.LENGTH_LONG).show();
            return;
        }
        // Only available for email/password accounts
        boolean hasPasswordProvider = false;
        for (UserInfo info : user.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                hasPasswordProvider = true;
                break;
            }
        }
        if (!hasPasswordProvider) {
            Toast.makeText(this,
                    "This account is not registered with email/password (e.g., Google).",
                    Toast.LENGTH_LONG).show();
            return;
        }

        final EditText currentEt = new EditText(this);
        currentEt.setHint("Current password");
        currentEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final EditText newEt = new EditText(this);
        newEt.setHint("New password (min. 6 characters)");
        newEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final EditText confirmEt = new EditText(this);
        confirmEt.setHint("Confirm new password");
        confirmEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);
        container.addView(currentEt);
        container.addView(newEt);
        container.addView(confirmEt);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Change password")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String cur = currentEt.getText().toString();
                    String npw = newEt.getText().toString();
                    String cfw = confirmEt.getText().toString();

                    if (TextUtils.isEmpty(cur) || TextUtils.isEmpty(npw) || TextUtils.isEmpty(cfw)) {
                        Toast.makeText(this, "All fields are required.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (npw.length() < 6) {
                        Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!npw.equals(cfw)) {
                        Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    doChangePassword(email, cur, npw);
                })
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
    }

    private void doChangePassword(String email, String currentPassword, String newPassword) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You are not signed in.", Toast.LENGTH_LONG).show();
            return;
        }
        passwordResetButton.setEnabled(false);
        Toast.makeText(this, "Changing password...", Toast.LENGTH_SHORT).show();

        user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword))
                .addOnSuccessListener(unused -> {
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(u ->
                                    Toast.makeText(this, "Password updated successfully.", Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Error updating password: " + readable(e.getMessage()),
                                            Toast.LENGTH_LONG).show())
                            .addOnCompleteListener(task -> passwordResetButton.setEnabled(true));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Reauth error: " + readable(e.getMessage()), Toast.LENGTH_LONG).show();
                    passwordResetButton.setEnabled(true);
                });
    }

    private String readable(String raw) {
        if (raw == null) return "unknown error";
        String low = raw.toLowerCase();
        if (raw.contains("PERMISSION_DENIED"))
            return "Permission denied (Firestore rules / API).";
        if (raw.contains("NOT_FOUND"))
            return "Document not found.";
        if (low.contains("network"))
            return "Network error. Check your internet connection.";
        if (low.contains("recent") || low.contains("requires recent login"))
            return "For security reasons, a recent login is required. Please sign out and sign in again.";
        if (low.contains("weak password") || low.contains("password should be at least"))
            return "Weak password. Use at least 6 characters.";
        if (low.contains("password is invalid") || low.contains("invalid password"))
            return "Incorrect current password.";
        return raw;
    }
}
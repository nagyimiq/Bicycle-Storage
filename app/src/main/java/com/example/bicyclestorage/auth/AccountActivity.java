package com.example.bicyclestorage.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bicyclestorage.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

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

        // Betöltjük az adatokat
        emailLabel.setText("Email: " + repo.getEmail());
        if (repo.userDoc() != null) {
            repo.userDoc().get()
                    .addOnSuccessListener(this::fillUser)
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Betöltési hiba: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }

        updateButton.setOnClickListener(v -> updateUsername());
        logoutButton.setOnClickListener(v -> {
            repo.logout();
            // Vissza loginra
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        passwordResetButton.setOnClickListener(v -> sendPasswordReset());
    }

    private void fillUser(DocumentSnapshot snap) {
        if (snap != null && snap.exists()) {
            String uname = snap.getString("username");
            if (uname != null) usernameField.setText(uname);
        }
    }

    private void updateUsername() {
        String newU = usernameField.getText().toString().trim();
        if (newU.isEmpty()) {
            Toast.makeText(this, "Felhasználónév nem lehet üres", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.updateUsername(newU)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Frissítve", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Hiba: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void sendPasswordReset() {
        String email = repo.getEmail();
        if (email == null) return;
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Jelszó visszaállító email elküldve", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Hiba: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
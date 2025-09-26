package com.example.bicyclestorage.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bicyclestorage.R;
import com.google.firebase.auth.FirebaseAuth;
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
                                    "Betöltési hiba: " + readable(e.getMessage()),
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

        passwordResetButton.setOnClickListener(v -> sendPasswordReset());
    }

    private void fillUser(DocumentSnapshot snap) {
        if (snap != null && snap.exists()) {
            String uname = snap.getString("username");
            if (uname != null) {
                usernameField.setText(uname);
            } else {
                usernameField.setHint("Nincs felhasználónév");
            }
        } else {
            // Dokumentum NEM létezik → létrehozzuk alapból
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
                        Toast.makeText(this, "Alap profil létrehozva.", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Nem sikerült alap profilt létrehozni: " + readable(e.getMessage()),
                                    Toast.LENGTH_LONG).show());
        }
    }

    private String deriveDefaultUsername(String email) {
        if (email == null) return "felhasznalo";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
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
                        Toast.makeText(this, "Hiba: " + readable(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    private void sendPasswordReset() {
        String email = repo.getEmail();
        if (email == null) return;
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Jelszó visszaállító email elküldve", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Hiba: " + readable(e.getMessage()), Toast.LENGTH_LONG).show());
    }

    private String readable(String raw) {
        if (raw == null) return "ismeretlen hiba";
        if (raw.contains("PERMISSION_DENIED"))
            return "Nincs jogosultság (Firestore rules / API).";
        if (raw.contains("NOT_FOUND"))
            return "Dokumentum nem található.";
        return raw;
    }
}
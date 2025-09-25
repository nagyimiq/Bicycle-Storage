package com.example.bicyclestorage.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bicyclestorage.MainActivity;
import com.example.bicyclestorage.R;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private EditText emailField, passwordField, usernameField;
    private FirebaseAuth auth;
    private final FirebaseUserRepository repo = new FirebaseUserRepository();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            // Ha már bejelentkezett valaki
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_register);

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        usernameField = findViewById(R.id.usernameField);
        Button registerButton = findViewById(R.id.registerButton);
        TextView toLogin = findViewById(R.id.toLogin);

        registerButton.setOnClickListener(v -> doRegister());
        toLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void doRegister() {
        String email = emailField.getText().toString().trim();
        String pass = passwordField.getText().toString().trim();
        String username = usernameField.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Minden mezőt tölts ki!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pass.length() < 6) {
            Toast.makeText(this, "Jelszó legalább 6 karakter!", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    String uid = res.getUser().getUid();
                    repo.createUserProfile(uid, email, username)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Sikeres regisztráció", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Profil mentési hiba: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Regisztráció hiba: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
package com.example.bicyclestorage.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bicyclestorage.MainActivity;
import com.example.bicyclestorage.R;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText emailField, passwordField;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        setContentView(R.layout.activity_login);

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        Button loginButton = findViewById(R.id.loginButton);
        TextView toRegister = findViewById(R.id.toRegister);

        Intent intent = getIntent();
        boolean regSuccess = intent != null && intent.getBooleanExtra("reg_success", false);
        Log.d(TAG, "onCreate regSuccess=" + regSuccess + " currentUser=" + auth.getCurrentUser());

        if (regSuccess) {
            String prefill = intent.getStringExtra("prefill_email");
            if (prefill != null) emailField.setText(prefill);
            // Itt – ha akarod – kijelentkeztetheted biztos ami biztos:
            if (auth.getCurrentUser() != null) {
                auth.signOut();
                Log.d(TAG, "signOut a regisztrációs visszatéréskor");
            }
            Toast.makeText(this, "Sikeres regisztráció! Jelentkezz be.", Toast.LENGTH_LONG).show();
        } else {
            // Normál autologin
            if (auth.getCurrentUser() != null) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return;
            }
        }

        loginButton.setOnClickListener(v -> doLogin());
        toRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void doLogin() {
        String email = emailField.getText().toString().trim();
        String pass  = passwordField.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Email és jelszó kell!", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(res -> {
                    Toast.makeText(this, "Sikeres bejelentkezés", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Hiba: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
package com.example.incidex

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar

class AdminUsers : AppCompatActivity() {

    private var users: List<AppUser> = emptyList()   // registered users only (never guests, never admins)
    private var query = ""
    private var registration: ListenerRegistration? = null

    private lateinit var adapter: AdminUserAdapter
    private lateinit var txtEmpty: TextView
    private lateinit var txtTotal: TextView
    private lateinit var txtWeek: TextView
    private lateinit var txtToday: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.admin_users)

        txtEmpty = findViewById(R.id.txtUsersEmpty)
        txtTotal = findViewById(R.id.txtUsersTotal)
        txtWeek = findViewById(R.id.txtUsersWeek)
        txtToday = findViewById(R.id.txtUsersToday)

        adapter = AdminUserAdapter(emptyList()) { anchor, user -> showMenu(anchor, user) }
        findViewById<RecyclerView>(R.id.rvAdminUsers).apply {
            layoutManager = LinearLayoutManager(this@AdminUsers)
            adapter = this@AdminUsers.adapter
        }

        findViewById<EditText>(R.id.edtUsersSearch).doAfterTextChanged {
            query = it?.toString().orEmpty().trim().lowercase()
            render()
        }

        findViewById<View>(R.id.btnAdminAddUser).setOnClickListener { showUserDialog(null) }

        AdminBottomNav.setup(
            this,
            findViewById<BottomNavigationView>(R.id.adminBottomNav),
            R.id.admin_nav_users
        )
    }

    // Live updates: the list refreshes by itself after every add, edit or delete
    override fun onStart() {
        super.onStart()
        registration?.remove()
        registration = AdminUsersRepository.listen(
            onChange = { list ->
                users = list.filter { it.role == AuthRepository.ROLE_USER }
                render()
            },
            onError = { toast(it) }
        )
    }

    override fun onStop() {
        registration?.remove()
        registration = null
        super.onStop()
    }

    // ---------------------------------------------------------------- list + analytics

    private fun render() {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // A user whose sign-up time is not known yet was just created
        txtTotal.text = users.size.toString()
        txtWeek.text = users.count { (it.createdAt ?: now) >= weekAgo }.toString()
        txtToday.text = users.count { (it.createdAt ?: now) >= startOfToday }.toString()

        val sorted = users.sortedByDescending { it.createdAt ?: Long.MAX_VALUE }
        val shown = if (query.isEmpty()) sorted else sorted.filter {
            it.name.lowercase().contains(query) || it.email.lowercase().contains(query)
        }

        adapter.submit(shown)
        txtEmpty.text =
            if (users.isEmpty()) "No registered users yet" else "No users match your search"
        txtEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- row menu

    private fun showMenu(anchor: View, user: AppUser) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Edit name")
        popup.menu.add(0, 2, 1, "Send password reset email")
        popup.menu.add(0, 3, 2, "Delete user")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showUserDialog(user)
                2 -> confirmReset(user)
                3 -> confirmDelete(user)
            }
            true
        }
        popup.show()
    }

    // ---------------------------------------------------------------- add / edit

    private fun showUserDialog(user: AppUser?) {
        val view = layoutInflater.inflate(R.layout.admin_dialog_user, null)
        val tilName = view.findViewById<TextInputLayout>(R.id.tilUserName)
        val tilEmail = view.findViewById<TextInputLayout>(R.id.tilUserEmail)
        val tilPassword = view.findViewById<TextInputLayout>(R.id.tilUserPassword)
        val edtName = view.findViewById<TextInputEditText>(R.id.edtUserName)
        val edtEmail = view.findViewById<TextInputEditText>(R.id.edtUserEmail)
        val edtPassword = view.findViewById<TextInputEditText>(R.id.edtUserPassword)

        if (user != null) {
            edtName.setText(user.name)
            edtEmail.setText(user.email)
            edtEmail.isEnabled = false                       // an email can't be changed from here
            tilEmail.helperText = "Email can't be changed here"
            tilPassword.visibility = View.GONE               // passwords can't be set or seen
        }

        edtName.doAfterTextChanged { tilName.error = null }
        edtEmail.doAfterTextChanged { tilEmail.error = null }
        edtPassword.doAfterTextChanged { tilPassword.error = null }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (user == null) "Add user" else "Edit user")
            .setView(view)
            .setPositiveButton(if (user == null) "Create" else "Save", null)  // set below, so errors keep it open
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val name = edtName.text.toString().trim()
                val email = edtEmail.text.toString().trim().lowercase()
                val password = edtPassword.text.toString()

                var ok = true
                if (name.length < 2) {
                    tilName.error = "Enter the full name"
                    ok = false
                }
                if (user == null) {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        tilEmail.error = "Enter a valid email address"
                        ok = false
                    }
                    if (password.length < 6) {
                        tilPassword.error = "At least 6 characters"
                        ok = false
                    }
                }
                if (!ok) return@setOnClickListener

                if (user == null) {
                    positive.isEnabled = false
                    AdminUsersRepository.addUser(this, name, email, password) { error ->
                        if (isFinishing || isDestroyed) return@addUser
                        positive.isEnabled = true
                        when {
                            error == null -> {
                                dialog.dismiss()
                                toast("User created")
                            }
                            error.contains("already exists") -> tilEmail.error = error
                            else -> toast(error)
                        }
                    }
                } else if (name == user.name) {
                    dialog.dismiss()   // nothing changed
                } else {
                    positive.isEnabled = false
                    AdminUsersRepository.updateName(user.uid, name) { error ->
                        if (isFinishing || isDestroyed) return@updateName
                        positive.isEnabled = true
                        if (error == null) {
                            dialog.dismiss()
                            toast("User updated")
                        } else {
                            toast(error)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    // ---------------------------------------------------------------- reset / delete

    private fun confirmReset(user: AppUser) {
        AlertDialog.Builder(this)
            .setTitle("Send password reset?")
            .setMessage("A reset link will be emailed to ${user.email}.")
            .setPositiveButton("Send") { _, _ ->
                AuthRepository.sendReset(user.email) { error ->
                    toast(error ?: "Reset email sent to ${user.email}")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(user: AppUser) {
        AlertDialog.Builder(this)
            .setTitle("Delete user?")
            .setMessage(
                "${user.name.ifBlank { user.email }} will be removed from Incidex and " +
                        "can no longer log in."
            )
            .setPositiveButton("Delete") { _, _ ->
                AdminUsersRepository.deleteUser(user.uid) { error ->
                    toast(error ?: "User deleted")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
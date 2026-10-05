package net.robrion.dompetnotif

import android.app.Application
import androidx.room.Room
import net.robrion.dompetnotif.data.Prefs
import net.robrion.dompetnotif.data.TxnDatabase

class DompetNotifApp : Application() {

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        db = Room.databaseBuilder(
            this,
            TxnDatabase::class.java,
            "dompetnotif.db"
        ).fallbackToDestructiveMigration() // v1 belum rilis publik; aman
            .build()
    }

    companion object {
        lateinit var db: TxnDatabase
            private set
    }
}

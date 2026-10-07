package com.psiqos.spheres

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

/** Buys power-ups in packs with the dot account of one difficulty; every purchase is confirmed. */
class ShopActivity : Activity() {

    private lateinit var difficulty: Difficulty
    private lateinit var wallet: Wallet

    private class Row(val name: Int, val count: TextView, val buy: TextView)

    private lateinit var rows: Map<PowerUp, Row>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)
        difficulty = Difficulty.parse(intent.getStringExtra(Difficulty.EXTRA))
        findViewById<TextView>(R.id.shop_back).setOnClickListener { finish() }

        rows = mapOf(
            PowerUp.SHRINKER to Row(R.string.powerup_shrinker, findViewById(R.id.shop_shrinker_count), findViewById(R.id.shop_shrinker_buy)),
            PowerUp.TIME_STOP to Row(R.string.powerup_time_stop, findViewById(R.id.shop_time_stop_count), findViewById(R.id.shop_time_stop_buy)),
            PowerUp.EXPANDER to Row(R.string.powerup_expander, findViewById(R.id.shop_expander_count), findViewById(R.id.shop_expander_buy)),
        )
        findViewById<TextView>(R.id.shop_shrinker_note).setText(R.string.shop_shrinker_note)
        findViewById<TextView>(R.id.shop_time_stop_note).text =
            getString(R.string.shop_time_stop_note, PowerUp.TIME_STOP_SECONDS, PowerUp.EXTRA_MOVES_COUNT)
        findViewById<TextView>(R.id.shop_expander_note).setText(R.string.shop_expander_note)
        for ((p, row) in rows) {
            row.buy.text = getString(R.string.shop_pack, p.packSize, p.packPrice)
            row.buy.setOnClickListener { offer(p, row) }
        }
    }

    override fun onResume() {
        super.onResume()
        wallet = Prefs.wallet(this, difficulty)
        refresh()
    }

    private fun offer(p: PowerUp, row: Row) {
        if (!wallet.canBuyPack(p)) {
            Toast.makeText(this, getString(R.string.powerup_too_expensive, p.packPrice - wallet.dots), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.shop_confirm, p.packSize, getString(row.name), p.packPrice))
            .setPositiveButton(R.string.shop_buy) { _, _ ->
                // Read again: the account may have changed while the dialog was open.
                wallet = Prefs.wallet(this, difficulty)
                if (wallet.buyPack(p)) Prefs.saveWallet(this, difficulty, wallet)
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refresh() {
        findViewById<TextView>(R.id.shop_wallet).text =
            getString(R.string.wallet_at, wallet.dots, getString(difficulty.label))
        for ((p, row) in rows) {
            row.count.text = getString(R.string.shop_owned, wallet.count(p))
            row.buy.alpha = if (wallet.canBuyPack(p)) 1f else 0.4f
        }
    }
}

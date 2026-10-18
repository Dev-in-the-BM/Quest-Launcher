package quest.devinthebm.launcher

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import quest.devinthebm.launcher.data.BuiltInWallpapers
import quest.devinthebm.launcher.ui.WallpaperGridAdapter

/** Lets the user set the device wallpaper from the launcher's bundled set, pulled
 * straight from [BuiltInWallpapers] — no need to leave the launcher for Photos. */
class WallpaperPickerActivity : BaseListActivity() {

    private lateinit var adapter: WallpaperGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        titleView.text = getString(R.string.title_wallpaper_picker)

        adapter = WallpaperGridAdapter(onClick = { name -> applyWallpaper(name) })
        listView.layoutManager = GridLayoutManager(this, COLUMNS)
        listView.adapter = adapter
        adapter.submit(BuiltInWallpapers.names())

        softKeys.setLabels(getString(R.string.softkey_back), null, getString(R.string.wallpaper_picker_more))
        softKeys.setOnLeftClick { finish() }
        softKeys.setOnRightClick { openSystemChooser() }
        focusFirst()
    }

    private fun applyWallpaper(name: String) {
        val resId = BuiltInWallpapers.resId(this, name)
        if (resId == 0) return
        Thread {
            try {
                val bitmap = BitmapFactory.decodeResource(resources, resId)
                WallpaperManager.getInstance(this).setBitmap(bitmap)
                runOnUiThread {
                    if (!isDestroyed) {
                        Toast.makeText(this, R.string.wallpaper_set_toast, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isDestroyed) Toast.makeText(this, "Couldn't set wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun openSystemChooser() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.opt_set_wallpaper)))
        } catch (e: Exception) {
            Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val COLUMNS = 2
    }
}

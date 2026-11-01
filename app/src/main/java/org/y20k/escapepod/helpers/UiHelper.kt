/*
 * UiHelper.kt
 * Implements the UiHelper object
 * A UiHelper provides helper methods for User Interface related tasks
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-26 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.helpers

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import java.io.ByteArrayOutputStream


/*
 * UiHelper object
 */
object UiHelper {

    /* Define log tag */
    private val TAG: String = UiHelper::class.java.simpleName


    /* Get scaling factor from display density */
    fun getDensityScalingFactor(context: Context): Float {
        return context.resources.displayMetrics.density
    }


    /* Sets layout margins for given view in DP */
    fun setViewMargins(context: Context, view: View, left: Int = 0, right: Int = 0, top: Int= 0, bottom: Int = 0) {
        val l: Int = (left * getDensityScalingFactor(context)).toInt()
        val r: Int = (right * getDensityScalingFactor(context)).toInt()
        val t: Int = (top * getDensityScalingFactor(context)).toInt()
        val b: Int = (bottom * getDensityScalingFactor(context)).toInt()
        if (view.layoutParams is ViewGroup.MarginLayoutParams) {
            val p = view.layoutParams as ViewGroup.MarginLayoutParams
            p.setMargins(l, t, r, b)
            view.requestLayout()
        }
    }


    /* Sets layout margins for given view in percent */
    fun setViewMarginsPercentage(context: Context, view: View, height: Int, width: Int, left: Int = 0, right: Int = 0, top: Int= 0, bottom: Int = 0) {
        val l: Int = ((width / 100.0f) * left).toInt()
        val r: Int = ((width / 100.0f) * right).toInt()
        val t: Int = ((height / 100.0f) * top).toInt()
        val b: Int = ((height / 100.0f) * bottom).toInt()
        setViewMargins(context, view, l, r, t, b)
    }


    /* Creates a fading gradient from given start color to a given transparency value */
    fun createColorTransparencyGradientDrawable(view: View, materialColor: Int, transparency: Float): GradientDrawable {
        // get colors
        val startColor = MaterialColors.getColor(view, materialColor, Color.WHITE)
        val endColor: Int = ColorUtils.setAlphaComponent(startColor, (255 * transparency).toInt())
        // create gradient drawable
        val gradientDrawable = GradientDrawable()
        gradientDrawable.shape = GradientDrawable.RECTANGLE
        gradientDrawable.colors = intArrayOf(
            startColor, /* start color */
            /* centerColor (not set) */
            endColor /* end color */
        )
        gradientDrawable.gradientType = GradientDrawable.LINEAR_GRADIENT
        gradientDrawable.orientation = GradientDrawable.Orientation.BOTTOM_TOP
        return gradientDrawable
    }


    /* Get the height of the navigation bar - used for older Android versions */
    fun getNavigationBarHeight(context: Context): Int {
        // determine the resource name based on device orientation
        val orientation = context.resources.configuration.orientation
        val resourceName: String
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            resourceName = "navigation_bar_height"
        } else {
            resourceName = "navigation_bar_height_landscape"
        }
        // try to get the resource identifier
        val resources = context.resources
        val resourceId = resources.getIdentifier(resourceName, "dimen", "android")
        // return the height if found
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId)
        } else {
            return 0
        }
    }


    /* Compute the height of the player container - used to add the padding needed to not have the player container hide list items */
    fun getPlayerContainerHeight(context: Context, playerVisible: Boolean, upNextVisible: Boolean): Int {
        val density = getDensityScalingFactor(context)
        var padding: Int = Keys.LIST_DEFAULT_BOTTOM_PADDING
        if (playerVisible) padding = ((Keys.PLAYER_HEIGHT + Keys.PLAYER_BOTTOM_MARGIN + Keys.LIST_DEFAULT_BOTTOM_PADDING) * density).toInt()
        if (upNextVisible) padding = ((Keys.PLAYER_HEIGHT + Keys.PLAYER_BOTTOM_MARGIN + Keys.UP_NEXT_HEIGHT + Keys.UP_NEXT_BOTTOM_MARGIN + Keys.LIST_DEFAULT_BOTTOM_PADDING) * density).toInt()
        return padding
    }


    /* Get the default podcast cover as a ByteArray */
    fun getDefaultCoverAsByteArray(context: Context, size: Int = 512): ByteArray {
        val coverBitmap: Bitmap = ContextCompat.getDrawable(context, R.drawable.ic_default_cover_rss_icon_192dp)!!.toBitmap(size, size)
        val stream = ByteArrayOutputStream()
        coverBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val coverByteArray: ByteArray = stream.toByteArray()
        coverBitmap.recycle()
        return coverByteArray
    }


    /* Hide keyboard */
    fun hideSoftKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }


    /* Show keyboard */
    fun showSoftKeyboard(context: Context, view: View) {
        if (view.requestFocus()) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }


    /*
     * Inner class: Callback that detects a left swipe
     * Credit: https://github.com/kitek/android-rv-swipe-delete/blob/master/app/src/main/java/pl/kitek/rvswipetodelete/SwipeToDeleteCallback.kt
     */
    abstract class SwipeToDeleteCallback(context: Context): ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

        private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_remove_circle_24dp)
        private val intrinsicWidth: Int = deleteIcon?.intrinsicWidth ?: 0
        private val intrinsicHeight: Int = deleteIcon?.intrinsicHeight ?: 0
        private val background: ColorDrawable = ColorDrawable()
        private val backgroundColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorErrorContainer, null)
        private val clearPaint: Paint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            // disable swipe for the add new card
            if (viewHolder.itemViewType == Keys.VIEW_TYPE_ADD_NEW) {
                return 0
            }
            return super.getMovementFlags(recyclerView, viewHolder)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            // do nothing
            return false
        }

        override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
            val itemView = viewHolder.itemView
            val itemHeight = itemView.bottom - itemView.top
            val isCanceled = dX == 0f && !isCurrentlyActive

            if (isCanceled) {
                clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                return
            }

            // draw red delete background
            background.color = backgroundColor
            background.setBounds(
                    itemView.right + dX.toInt(),
                    itemView.top,
                    itemView.right,
                    itemView.bottom
            )
            background.draw(c)

            // calculate position of delete icon
            val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
            val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
            val deleteIconLeft = itemView.right - deleteIconMargin - intrinsicWidth
            val deleteIconRight = itemView.right - deleteIconMargin
            val deleteIconBottom = deleteIconTop + intrinsicHeight

            // draw delete icon
            deleteIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
            deleteIcon?.draw(c)

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
            c?.drawRect(left, top, right, bottom, clearPaint)
        }
    }
    /*
     * End of inner class
     */

}
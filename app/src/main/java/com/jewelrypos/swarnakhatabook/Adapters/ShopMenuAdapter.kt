package com.jewelrypos.swarnakhatabook.Adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.jewelrypos.swarnakhatabook.DataClasses.ShopDetails
import com.jewelrypos.swarnakhatabook.R

/**
 * Adapter for the shop dropdown list
 */
class ShopMenuAdapter(
    private val context: Context,
    private var shops: List<ShopDetails>,
    private var activeShopId: String? = null
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val VIEW_TYPE_SHOP = 0
    private val VIEW_TYPE_CREATE_NEW = 1

    override fun getCount(): Int = shops.size + 1 // +1 for "Create New Shop" item

    override fun getItem(position: Int): Any {
        return if (position < shops.size) {
            shops[position]
        } else {
            "CREATE_NEW_SHOP" // Special identifier for create new shop option
        }
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int {
        return if (position < shops.size) VIEW_TYPE_SHOP else VIEW_TYPE_CREATE_NEW
    }

    override fun getViewTypeCount(): Int = 2

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return if (getItemViewType(position) == VIEW_TYPE_SHOP) {
            getShopView(position, convertView, parent)
        } else {
            getCreateNewView(convertView, parent)
        }
    }

    private fun getShopView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.shop_dropdown_item, parent, false)
        
        val shop = shops[position]
        
        view.findViewById<TextView>(R.id.shop_name).text = shop.shopName
        
        // Show active indicator if this is the active shop
        val activeIndicator = view.findViewById<ImageView>(R.id.active_indicator)
        activeIndicator.visibility = if (shop.shopId == activeShopId) View.VISIBLE else View.GONE
        
        return view
    }

    private fun getCreateNewView(convertView: View?, parent: ViewGroup?): View {
        return convertView ?: inflater.inflate(R.layout.create_shop_dropdown_item, parent, false)
    }

    /**
     * Update the list of shops and active shop ID
     */
    fun updateData(newShops: List<ShopDetails>, newActiveShopId: String?) {
        shops = newShops
        activeShopId = newActiveShopId
        notifyDataSetChanged()
    }

    /**
     * Update just the active shop ID
     */
    fun updateActiveShop(newActiveShopId: String?) {
        activeShopId = newActiveShopId
        notifyDataSetChanged()
    }
} 
package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge
import com.jewelrypos.swarnakhatabook.databinding.ItemExtraChargeBinding


class ExtraChargeAdapter : RecyclerView.Adapter<ExtraChargeAdapter.ChargeViewHolder>() {

    private val charges = mutableListOf<ExtraCharge>()

    var onDeleteClickListener: ((ExtraCharge) -> Unit)? = null
    var onChargesUpdatedListener: (() -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChargeViewHolder {
        val binding = ItemExtraChargeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChargeViewHolder(binding)
    }

    fun getTotalCharges(): Double {
        return charges.sumOf { it.amount }
    }

    fun getExtraChargeList(): List<ExtraCharge> {
        return charges.toList()
    }

    override fun onBindViewHolder(holder: ChargeViewHolder, position: Int) {
        holder.bind(charges[position])
    }

    override fun getItemCount(): Int = charges.size

    fun updateCharges(newCharges: List<ExtraCharge>) {
        val diffCallback = ChargeDiffCallback(charges, newCharges)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        charges.clear()
        charges.addAll(newCharges)
        diffResult.dispatchUpdatesTo(this)
        onChargesUpdatedListener?.invoke()
    }

    fun addCharge(charge: ExtraCharge) {
        val newList = charges.toMutableList().apply {
            add(charge)
        }
        updateCharges(newList)
    }

    fun removeCharge(charge: ExtraCharge) {
        val newList = charges.toMutableList().apply {
            remove(charge)
        }
        updateCharges(newList)
    }

    inner class ChargeViewHolder(private val binding: ItemExtraChargeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(charge: ExtraCharge) {
            binding.apply {
                chargeNameText.text = charge.name
                chargeAmountText.text = charge.amount.toString()

                deleteChargeButton.setOnClickListener {
                    onDeleteClickListener?.invoke(charge)
                }
            }
        }
    }

    class ChargeDiffCallback(
        private val oldList: List<ExtraCharge>,
        private val newList: List<ExtraCharge>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
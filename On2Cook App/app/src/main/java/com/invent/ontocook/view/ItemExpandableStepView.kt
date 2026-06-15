package com.invent.ontocook.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.bumptech.glide.Glide
import com.invent.ontocook.R
import kotlinx.android.synthetic.main.item_expandable_step_view.view.*
import kotlinx.android.synthetic.main.item_step_view.view.clStepView
import kotlinx.android.synthetic.main.item_step_view.view.ivArrowDown
import kotlinx.android.synthetic.main.item_step_view.view.ivBottomCurveView
import kotlinx.android.synthetic.main.item_step_view.view.ivRight
import kotlinx.android.synthetic.main.item_step_view.view.ivStepImage
import kotlinx.android.synthetic.main.item_step_view.view.ivTime
import kotlinx.android.synthetic.main.item_step_view.view.tvCurrentStep
import kotlinx.android.synthetic.main.item_step_view.view.tvStepDesc
import kotlinx.android.synthetic.main.item_step_view.view.tvStepName
import kotlinx.android.synthetic.main.item_step_view.view.tvTime
import kotlinx.android.synthetic.main.item_step_view.view.viewIndicatorBottom
import kotlinx.android.synthetic.main.item_step_view.view.viewIndicatorTop

class ItemExpandableStepView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LinearLayout(context, attrs, defStyleAttr) {

    init {
        initView(attrs)
    }

    private fun initView(attrs: AttributeSet?){
        View.inflate(context, R.layout.item_expandable_step_view, this)

        expansionLayoutView.addIndicatorListener { _, willExpand ->
            if(willExpand){
                ivArrowDown.setImageResource(R.drawable.ic_toggle_up_grey)
            }else{
                ivArrowDown.setImageResource(R.drawable.ic_toggle_down_grey)
            }
        }
    }

    fun setDefaultView(id : Int, name : String, desc : String,
                       time : String, currentStep : String,
                       image : String = ""){
        this.id = id
        tvStepName.text = name
        tvStepDesc.text = desc
        tvTime.text = time
        viewIndicatorTop.text = ""

        //ivTopCurveView.visibility = View.VISIBLE
        viewIndicatorTop.visibility = View.VISIBLE
        tvCurrentStep.visibility = View.VISIBLE
        tvCurrentStep.text = currentStep

        Glide
            .with(context)
            .load(image)
            .centerInside()
            .placeholder(R.drawable.ic_pizza)
            .into(rootView.ivStepImage)
    }

    fun setDefaultView(id : Int, name : String, desc : String,
                       time : String, currentStep : String, action : String,
                       image : Int){
        this.id = id
        tvStepName.text = name
        tvStepDesc.text = desc
        tvTime.text = time
        viewIndicatorTop.text = action
        ivStepImage.setImageResource(image)

        //ivTopCurveView.visibility = View.VISIBLE
        viewIndicatorTop.visibility = View.VISIBLE
        tvCurrentStep.visibility = View.VISIBLE
        tvCurrentStep.text = currentStep
    }

    fun setCurrentProgressView(id : Int){
//        ivTopCurveView.colorFilter =
//            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
//                ContextCompat.getColor(
//                    context,
//                    R.color.orange
//                ), BlendModeCompat.SRC_IN
//            )

        viewIndicatorTop.background.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    context,
                    R.color.orange
                ), BlendModeCompat.SRC_IN
            )
        clStepView.background.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    context,
                    R.color.orange
                ), BlendModeCompat.SRC_IN
            )
        this.id = id

        ivTime.setImageResource(R.drawable.ic_time)
        ivArrowDown.setImageResource(R.drawable.ic_arrow_down_white)
        viewIndicatorTop.setTextColor(resources.getColor(R.color.white))
        tvStepName.setTextColor(resources.getColor(R.color.white))
        tvStepDesc.setTextColor(resources.getColor(R.color.white))
        tvTime.setTextColor(resources.getColor(R.color.white))

        //ivTopCurveView.visibility = View.VISIBLE
        viewIndicatorTop.visibility = View.VISIBLE
        tvCurrentStep.visibility = View.GONE
        tvCurrentStep.text = ""
        this.visibility = View.GONE
    }

    fun setLayoutParam(topMargin : Int){
        var layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.topMargin = topMargin
        rootView.layoutParams = layoutParams
    }

    fun setCompletedProgressView(){
        tvCurrentStep.visibility = View.GONE
        ivRight.visibility = View.VISIBLE
        //ivTopCurveView.visibility = View.GONE
        viewIndicatorTop.visibility = View.GONE
        ivBottomCurveView.visibility = View.VISIBLE
        viewIndicatorBottom.visibility = View.VISIBLE
        viewIndicatorBottom.background.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    context,
                    R.color.dark_grey
                ), BlendModeCompat.SRC_IN
            )
        clStepView.background.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    context,
                    R.color.dark_grey
                ), BlendModeCompat.SRC_IN
            )
    }

    fun setRemainProgressView(){
        ivRight.visibility = View.GONE
        tvCurrentStep.visibility = View.VISIBLE
        viewIndicatorTop.visibility = View.VISIBLE
        //ivTopCurveView.visibility = View.VISIBLE
        ivBottomCurveView.visibility = View.GONE
        viewIndicatorBottom.visibility = View.GONE
        viewIndicatorTop.background.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    context,
                    R.color.white
                ), BlendModeCompat.SRC_IN
            )
        clStepView.background.colorFilter =
            BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                ContextCompat.getColor(
                    context,
                    R.color.white
                ), BlendModeCompat.SRC_IN
            )
    }
}
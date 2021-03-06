package cash.z.android.wallet.ui.fragment

import android.os.Bundle
import android.os.Handler
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.FragmentHomeBinding
import cash.z.android.wallet.di.annotation.FragmentScope
import cash.z.android.wallet.extention.*
import cash.z.android.wallet.sample.SampleProperties
import cash.z.android.wallet.ui.adapter.TransactionAdapter
import cash.z.android.wallet.ui.presenter.HomePresenter
import cash.z.android.wallet.ui.presenter.HomePresenterModule
import cash.z.android.wallet.ui.util.AlternatingRowColorDecoration
import cash.z.android.wallet.ui.util.LottieLooper
import cash.z.android.wallet.ui.util.TopAlignedSpan
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.ActiveSendTransaction
import cash.z.wallet.sdk.data.ActiveTransaction
import cash.z.wallet.sdk.data.TransactionState
import cash.z.wallet.sdk.data.twig
import cash.z.wallet.sdk.ext.*
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random
import kotlin.random.nextLong


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class HomeFragment : BaseFragment(), SwipeRefreshLayout.OnRefreshListener, HomePresenter.HomeView {

    @Inject
    lateinit var homePresenter: HomePresenter

    private lateinit var binding: FragmentHomeBinding
    private lateinit var zcashLogoAnimation: LottieLooper
    private var maxTransactionsShown: Int = 12
    private var snackbar: Snackbar? = null
    private var viewsInitialized = false

    //testing this
    private var clock: Handler = Handler()
    private val tickIfNeeded = Ticker()


    //
    // LifeCycle
    //

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewsInitialized = false
//        setupSharedElementTransitions()
        return DataBindingUtil.inflate<FragmentHomeBinding>(
            inflater, R.layout.fragment_home, container, false
        ).let {
            binding = it
            it.root
        }
    }

    private fun setupSharedElementTransitions() {
        TransitionInflater.from(mainActivity).inflateTransition(R.transition.transition_zec_sent).apply {
            duration = 3000L
            addListener(HomeTransitionListener())
            this@HomeFragment.sharedElementEnterTransition = this
            this@HomeFragment.sharedElementReturnTransition = this
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initTemp()
        init()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.setToolbarShown(false)
        mainActivity?.setDrawerLocked(false)
        initFab()

        binding.includeContent.recyclerTransactions.apply {
            layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            adapter = TransactionAdapter()
            addItemDecoration(AlternatingRowColorDecoration())
        }
        binding.includeContent.textTransactionHeaderSeeAll.setOnClickListener {
            mainActivity?.navController?.navigate(R.id.nav_history_fragment)
        }
    }

    override fun onResume() {
        super.onResume()
        launch {
            homePresenter.start()
        }
        clock.postDelayed(tickIfNeeded, 1000L)
    }

    override fun onPause() {
        super.onPause()
        clock.removeCallbacks(tickIfNeeded)
        homePresenter.stop()
        binding.lottieZcashBadge.cancelAnimation()
    }


    //
    // SendView Implementation
    //

    override fun setTransactions(transactions: List<WalletTransaction>) {
        val recent = if(transactions.size > maxTransactionsShown) transactions.subList(0, maxTransactionsShown) else transactions
        with (binding.includeContent.recyclerTransactions) {
            (adapter as TransactionAdapter).submitList(recent)
            postDelayed({
                smoothScrollToPosition(0)
            }, 100L)
        }
        // Show "See All" when we have a sublist on screen
        if (recent.size != transactions.size) {
            binding.includeContent.textTransactionHeaderSeeAll.visibility = View.VISIBLE
        }

        onContentRefreshComplete(transactions.isEmpty())
    }

    //TODO: pull some of this logic into the presenter, particularly the part that deals with ZEC <-> USD price conversion
    override fun updateBalance(old: Long, new: Long) {
        val zecValue = new.convertZatoshiToZec()
        setZecValue(zecValue.toZecString(3))
        setUsdValue(zecValue.convertZecToUsd(SampleProperties.USD_PER_ZEC).toUsdString())

        onContentRefreshComplete(new <= 0)
    }

    override fun setActiveTransactions(activeTransactionMap: Map<ActiveTransaction, TransactionState>) {
        if (activeTransactionMap.isEmpty()) {
            twig("A.T.: setActiveTransactionsShown(false) because map is empty")
            setActiveTransactionsShown(false)
            return
        }

        val transactions = activeTransactionMap.entries.toTypedArray()
        // primary is the last one that was inserted
        val primaryEntry = transactions[transactions.size - 1]
        updatePrimaryTransaction(primaryEntry.key, primaryEntry.value)

        onContentRefreshComplete(false)
    }

    override fun onCancelledTooLate() {
        snackbar = snackbar.showOk(view!!, "Oops! It was too late to cancel!")
    }

    override fun onSynchronizerError(error: Throwable?): Boolean {
        context?.alert(
            message = "WARNING: A critical error has occurred and " +
                    "this app will not function properly until that is corrected!",
            positiveButtonResId = R.string.ignore,
            negativeButtonResId = R.string.details,
            negativeAction = { context?.alert("Synchronization error:\n\n$error") }
        )
        return false
    }

    //
    // View API
    //

    fun setContentViewShown(isShown: Boolean) {
//        with(binding.includeContent) {
//            groupEmptyViewItems.visibility = if (isShown) View.GONE else View.VISIBLE
//            groupContentViewItems.visibility = if (isShown) View.VISIBLE else View.GONE
//        }
        toggleViews(!isShown)
    }

    private val stopAnimation = Runnable {
        setRefreshAnimationPlaying(false).also { twig("refresh false from onRefresh") }
    }
    override fun onRefresh() {
        setRefreshAnimationPlaying(true).also { twig("refresh true from onRefresh") }

        with(binding.includeContent.refreshLayout) {
            isRefreshing = false
            val fauxRefresh = Random.nextLong(750L..3000L)
            postDelayed(stopAnimation, fauxRefresh)
        }
    }


    fun setRefreshAnimationPlaying(isPlaying: Boolean) {
        twig("set refresh to: $isPlaying for $zcashLogoAnimation")
        if (isPlaying) {
            zcashLogoAnimation.start()
        } else {
            zcashLogoAnimation.stop()
        }
    }

    private fun onInitialLoadComplete() {
        val isEmpty = (binding.includeContent.recyclerTransactions?.adapter?.itemCount ?: 0).let { it == 0 }
        twig("onInitialLoadComplete and isEmpty == $isEmpty")
        setContentViewShown(!isEmpty)
        if (isEmpty) {
            binding.includeContent.textEmptyWalletMessage.setText(R.string.home_empty_wallet)
        }
        setRefreshAnimationPlaying(false).also { twig("refresh false from onInitialLoadComplete") }
    }

    private fun updatePrimaryTransaction(transaction: ActiveTransaction, transactionState: TransactionState) {

        twig("setting transaction state to ${transactionState::class.simpleName}")
        var title = binding.includeContent.textActiveTransactionTitle.text?.toString()  ?: ""
        var subtitle: CharSequence = binding.includeContent.textActiveTransactionSubtitle.text?.toString() ?: ""
        var isShown = binding.includeContent.textActiveTransactionHeader.visibility == View.VISIBLE
        var isShownDelay = 10L
        when (transactionState) {
            TransactionState.Creating -> {
                binding.includeContent.headerActiveTransaction.visibility = View.VISIBLE
                title = "Preparing ${transaction.value.convertZatoshiToZecString(3)} ZEC"
                subtitle = "to ${(transaction as ActiveSendTransaction).toAddress.truncate()}"
                setTransactionActive(transaction, true)
                isShown = true
            }
            TransactionState.SendingToNetwork -> {
                title = "Sending Transaction"
                subtitle = "to ${(transaction as ActiveSendTransaction).toAddress.truncate()}"
                binding.includeContent.textActiveTransactionValue.text = "${transaction.value.convertZatoshiToZecString(3)}"
                binding.includeContent.textActiveTransactionValue.visibility = View.VISIBLE
                binding.includeContent.buttonActiveTransactionCancel.visibility = View.GONE
                setTransactionActive(transaction, true)
                isShown = true
            }
            is TransactionState.Failure -> {
                binding.includeContent.lottieActiveTransaction.setAnimation(R.raw.lottie_send_failure)
                binding.includeContent.lottieActiveTransaction.playAnimation()
                title = "Failed"
                subtitle = when(transactionState.failedStep) {
                    TransactionState.Creating -> "Failed to create transaction"
                    TransactionState.SendingToNetwork -> "Failed to submit transaction to the network"
                    else -> "Unrecoginzed error"
                }
                binding.includeContent.buttonActiveTransactionCancel.visibility = View.GONE
                binding.includeContent.textActiveTransactionValue.visibility = View.GONE
                setTransactionActive(transaction, false)
                isShown = false
                isShownDelay = 10_000L
            }
            is TransactionState.AwaitingConfirmations -> {
                if (transactionState.confirmationCount < 1) {
                    binding.includeContent.lottieActiveTransaction.setAnimation(R.raw.lottie_send_success)
                    binding.includeContent.lottieActiveTransaction.playAnimation()
                    title = "ZEC Sent"
                    subtitle = "Waiting to be mined..."
                    binding.includeContent.textActiveTransactionValue.text = transaction.value.convertZatoshiToZecString(3)
                    binding.includeContent.textActiveTransactionValue.visibility = View.VISIBLE
                    binding.includeContent.buttonActiveTransactionCancel.visibility = View.GONE
                    isShown = true
                } else if (transactionState.confirmationCount > 1) {
                    isShown = false
                } else {
                    title = "Confirmation Received"
                    subtitle = transactionState.timestamp.toRelativeTimeString()
                    isShown = false
                    isShownDelay = 5_000L
                    // take it out of the list in a bit and skip counting confirmation animation for now (i.e. one is enough)
                }
            }
            is TransactionState.Cancelled -> {
                title = binding.includeContent.textActiveTransactionTitle.text.toString()
                subtitle = binding.includeContent.textActiveTransactionSubtitle.text.toString()
                setTransactionActive(transaction, false)
                isShown = false
                isShownDelay = 10_000L
            }
            else -> {
                Log.e(javaClass.simpleName, "Warning: unrecognized transaction state $transactionState is being ignored")
                return
            }
        }
        binding.includeContent.textActiveTransactionTitle.text = title
        binding.includeContent.textActiveTransactionSubtitle.text = subtitle
        twig("A.T.: setActiveTransactionsShown($isShown, $isShownDelay) because ${transactionState}")
        setActiveTransactionsShown(isShown, isShownDelay)
    }


    //
    // Internal View Logic
    //

    private fun setActiveTransactionsShown(isShown: Boolean, delay: Long = 0L) {
        binding.includeContent.headerActiveTransaction.postDelayed({
            binding.includeContent.groupActiveTransactionItems.visibility = if (isShown) View.VISIBLE else View.GONE
            // do not animate if visibility is already in the right state
//            binding.includeContent.headerActiveTransaction.animate().alpha(if(isShown) 1f else 0f).setDuration(250).setListener(
//                AnimatorCompleteListener{ }
//            )
        }, delay)
    }

    /**
     * General initialization called during onViewCreated. Mostly responsible for applying the default empty state of
     * the view, before any data or information is known.
     */
    private fun init() {
        zcashLogoAnimation = LottieLooper(binding.lottieZcashBadge, 20..47, 69)
        binding.includeContent.buttonActiveTransactionCancel.setOnClickListener {
            val transaction = it.tag as? ActiveSendTransaction
            if (transaction != null) {
                homePresenter.onCancelActiveTransaction(transaction)
            } else {
                Toaster.short("Error: unable to find transaction to cancel!")
            }
        }
        binding.lottieZcashBadge.setOnClickListener {
            binding.lottieZcashBadge.playAnimation()
        }

        binding.includeContent.refreshLayout.setProgressViewEndTarget(false, (38f * resources.displayMetrics.density).toInt())

        with(binding.includeContent.refreshLayout) {
            setOnRefreshListener(this@HomeFragment)
            setColorSchemeColors(R.color.zcashBlack.toAppColor())
            setProgressBackgroundColorSchemeColor(R.color.zcashYellow.toAppColor())
        }
        maxTransactionsShown = calculateMaxTransactions()

        // hide content
        setContentViewShown(false)
        binding.includeContent.textEmptyWalletMessage.setText(R.string.home_empty_wallet_updating)
        setRefreshAnimationPlaying(true).also { twig("refresh true from init") }
    }

    private fun calculateMaxTransactions(): Int {
        return 12 //TODO: measure the screen and get optimal number for this device
    }

    // initialize the stuff that is temporary and needs to go ASAP
    private fun initTemp() {

        with(binding.includeHeader) {
            headerFullViews = arrayOf(textBalanceUsd, textBalanceIncludesInfo, textBalanceZec, imageZecSymbolBalanceShadow, imageZecSymbolBalance)
            headerEmptyViews = arrayOf(textBalanceZecInfo, textBalanceZecEmpty, imageZecSymbolBalanceShadowEmpty, imageZecSymbolBalanceEmpty)
            headerFullViews.forEach { containerHomeHeader.removeView(it) }
            headerEmptyViews.forEach { containerHomeHeader.removeView(it) }
            binding.includeHeader.containerHomeHeader.visibility = View.INVISIBLE
        }

        // toggling determines visibility. hide it all.
        binding.includeContent.groupEmptyViewItems.visibility = View.GONE
        binding.includeContent.groupContentViewItems.visibility = View.GONE
    }

    /**
     * Initialize the Fab button and all its action items. Should be called during onActivityCreated.
     */
    private fun initFab() {
        val speedDial = binding.sdFab
        val nav = mainActivity?.navController

        HomeFab.values().forEach {
            speedDial.addActionItem(it.createItem())
        }

        speedDial.setOnActionSelectedListener { item ->
            HomeFab.fromId(item.id)?.destination?.apply { nav?.navigate(this) }
            false
        }
    }

    /**
     * Helper for creating fablets--those little buttons that pop up when the fab is tapped.
     */
    private val createItem: HomeFab.() -> SpeedDialActionItem = {
        SpeedDialActionItem.Builder(id, icon)
            .setFabBackgroundColor(bgColor.toAppColor())
            .setFabImageTintColor(R.color.zcashWhite.toAppColor())
            .setLabel(label.toAppString())
            .setLabelClickable(true)
            .create()
    }

    private fun setUsdValue(value: String) {
        val valueString = String.format("$ $value")
//        val hairSpace = "\u200A"
//        val adjustedValue = "$$hairSpace$valueString"
        val textSpan = SpannableString(valueString)
        textSpan.setSpan(TopAlignedSpan(), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textSpan.setSpan(TopAlignedSpan(), valueString.length - 3, valueString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.includeHeader.textBalanceUsd.text = textSpan
    }

    private fun setZecValue(value: String) {
        binding.includeHeader.textBalanceZec.text = value


//        // bugfix: there is a bug in motionlayout that causes text to flicker as it is resized because the last character doesn't fit. Padding both sides with a thin space works around this bug.
//        val hairSpace = "\u200A"
//        val adjustedValue = "$hairSpace$valueString$hairSpace"
//        text_balance_zec.text = adjustedValue
    }

    /**
     * Called whenever the content has been refreshed on the screen. When it is time to show and hide things.
     * If the balance goes to zero, the wallet is now empty so show the empty view.
     * If the balance changes from zero, the wallet is no longer empty so hide the empty view.
     * But don't do either of these things if the situation has not changed.
     */
    private fun onContentRefreshComplete(isEmpty: Boolean) {
        val isAdapterEmpty = (binding.includeContent.recyclerTransactions.adapter?.itemCount ?: 0) == 0
        val isBalanceZero = binding.includeHeader.textBalanceZec.text.toString() == "0"
        val isActiveHidden = binding.includeContent.groupActiveTransactionItems.visibility != View.VISIBLE
        val isActuallyEmpty = isEmpty && isAdapterEmpty && isBalanceZero && isActiveHidden

        // wasEmpty isn't enough info. it must be considered along with whether these views were ever initialized
        val wasEmpty = binding.includeContent.groupEmptyViewItems.visibility == View.VISIBLE
        // situation has changed when we weren't initialized but now we have a balance or emptiness has changed
        val situationHasChanged = !viewsInitialized || (isActuallyEmpty != wasEmpty)

        twig("onContentRefreshComplete called  initialized: $viewsInitialized  isEmpty: $isActuallyEmpty  wasEmpty: $wasEmpty")
        if (situationHasChanged) {
            twig("The situation has changed! toggling views!")
            setContentViewShown(!isActuallyEmpty)
        }

        setRefreshAnimationPlaying(false).also { twig("refresh false from onContentRefreshComplete") }
        binding.includeHeader.containerHomeHeader.visibility = View.VISIBLE
    }

    private fun onActiveTransactionTransitionStart() {
        binding.includeContent.buttonActiveTransactionCancel.visibility = View.INVISIBLE
    }

    private fun onActiveTransactionTransitionEnd() {
        // TODO: investigate if this fix is still required after getting transition animation working again
        // fixes a bug where the translation gets lost, during animation. As a nice side effect, visually, it makes the view appear to settle in to position
        binding.includeContent.headerActiveTransaction.translationZ = 10.0f
        binding.includeContent.buttonActiveTransactionCancel.apply {
            postDelayed({text  = "cancel"}, 50L)
            visibility = View.VISIBLE
        }
    }

    private fun setTransactionActive(transaction: ActiveTransaction, isActive: Boolean) {
        // TODO: get view for transaction, mostly likely keep a sparse array of these or something
        if (isActive) {
            binding.includeContent.buttonActiveTransactionCancel.setText(R.string.cancel)
            binding.includeContent.buttonActiveTransactionCancel.isEnabled = true
            binding.includeContent.buttonActiveTransactionCancel.tag = transaction
            binding.includeContent.headerActiveTransaction.animate().apply {
                translationZ(10f)
                duration = 200L
                interpolator = DecelerateInterpolator()
            }
        } else {
            binding.includeContent.buttonActiveTransactionCancel.setText(R.string.cancelled)
            binding.includeContent.buttonActiveTransactionCancel.isEnabled = false
            binding.includeContent.buttonActiveTransactionCancel.tag = null
            binding.includeContent.headerActiveTransaction.animate().apply {
                translationZ(2f)
                duration = 300L
                interpolator = AccelerateInterpolator()
            }
            binding.includeContent.lottieActiveTransaction.cancelAnimation()
        }
    }

    private inner class Ticker : Runnable {
        override fun run() {
            if (mainActivity == null) return
            binding.includeContent.recyclerTransactions.apply {
                if ((adapter?.itemCount ?: 0) > 0) {
                    adapter?.notifyDataSetChanged()
                }
                clock.postDelayed(this@Ticker, 1000L)
            }
        }
    }

    /**
     * Defines the basic properties of each FAB button for use while initializing the FAB
     */
    enum class HomeFab(
        @IdRes val id:Int,
        @DrawableRes val icon:Int,
        @ColorRes val bgColor:Int,
        @StringRes val label:Int,
        @IdRes val destination:Int
    ) {
        /* ordered by when they need to be added to the speed dial (i.e. reverse display order) */
        HISTORY(
            R.id.fab_history,
            R.drawable.ic_history_24dp,
            R.color.icon_request,
            R.string.destination_menu_label_history,
            R.id.nav_history_fragment
        ),
        RECEIVE(
            R.id.fab_receive,
            R.drawable.ic_qrcode_24dp,
            R.color.icon_receive,
            R.string.destination_menu_label_receive,
            R.id.nav_receive_fragment
        ),
        SEND(
            R.id.fab_send,
            R.drawable.ic_menu_send,
            R.color.icon_send,
            R.string.destination_menu_label_send,
            R.id.nav_send_fragment
        );

        companion object {
            fun fromId(id: Int): HomeFab? = values().firstOrNull { it.id == id }
        }
    }


//// ---------------------------------------------------------------------------------------------------------------------
//// TODO: Delete these test functions
//// ---------------------------------------------------------------------------------------------------------------------

    val delay = 50L
    lateinit var headerEmptyViews: Array<View>
    lateinit var headerFullViews: Array<View>


    fun forceRedraw() {
        view?.postDelayed({
            binding.includeHeader.containerHomeHeader.progress = binding.includeHeader.containerHomeHeader.progress - 0.1f
        }, delay * 2)
    }

    internal fun toggleViews(isEmpty: Boolean) {
        twig("toggling views to isEmpty == $isEmpty")
        var action: () -> Unit
        if (isEmpty) {
            action = {
                binding.includeContent.groupEmptyViewItems.visibility = View.VISIBLE
                binding.includeContent.groupContentViewItems.visibility = View.GONE
                headerFullViews.forEach { binding.includeHeader.containerHomeHeader.removeView(it) }
                headerEmptyViews.forEach {
                    tryIgnore {
                        binding.includeHeader.containerHomeHeader.addView(it)
                    }
                }
            }
        } else {
            action = {
                binding.includeContent.groupEmptyViewItems.visibility = View.GONE
                binding.includeContent.groupContentViewItems.visibility = View.VISIBLE
                headerEmptyViews.forEach { binding.includeHeader.containerHomeHeader.removeView(it) }
                headerFullViews.forEach {
                    tryIgnore {
                        binding.includeHeader.containerHomeHeader.addView(it)
                    }
                }
            }
        }
        view?.postDelayed({
            binding.includeHeader.containerHomeHeader.visibility = View.VISIBLE
            action()
            viewsInitialized = true
        }, delay)
        // TODO: the motion layout does not begin in the  right state for some reason. Debug this later.
        view?.postDelayed(::forceRedraw, delay * 2)
    }


    inner class HomeTransitionListener : Transition.TransitionListener {
        override fun onTransitionStart(transition: Transition) {
        }

        override fun onTransitionEnd(transition: Transition) {
        }

        override fun onTransitionResume(transition: Transition) {}
        override fun onTransitionPause(transition: Transition) {}
        override fun onTransitionCancel(transition: Transition) {}
    }
}


@Module
abstract class HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(modules = [HomePresenterModule::class])
    abstract fun contributeHomeFragment(): HomeFragment
}


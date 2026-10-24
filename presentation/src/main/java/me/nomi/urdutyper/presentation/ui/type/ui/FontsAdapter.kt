package me.nomi.urdutyper.presentation.ui.type.ui

import android.graphics.Typeface
import me.nomi.urdutyper.R
import me.nomi.urdutyper.databinding.ItemFontBinding
import me.nomi.urdutyper.presentation.ui.type.viewmodel.TypeViewModel
import me.nomi.urdutyper.presentation.utils.extensions.adapter.BaseAdapter

class FontsAdapter(private val viewModel: TypeViewModel) :
    BaseAdapter<Typeface, ItemFontBinding>(R.layout.item_font) {
    val pangrams = listOf(
        "A boy, Max, felt quick during his hazy weaving jumps.",
        "A large fawn jumped quickly over white zinc boxes.",
        "Viewing quizzical abstracts mixed up hefty jocks.",
        "Five wine experts jokingly quizzed sample Chablis.",
        "William Jex quickly caught five dozen Republicans.",
        "The vixen jumped quickly on her foe barking with zeal.",
        "Harry, jogging quickly, axed zen monks with beef vapor.",
        "Five or six big jet planes zoomed quickly by the tower.",
        "Six big devils from Japan quickly forgot how to waltz.",
        "Big July earthquakes confound zany experimental vow.",
        "Exquisite farm wench gives body jolt to prize stinker.",
        "My grandfather picks up quartz and valuable onyx jewels.",
        "Six crazy kings vowed to abolish my quite pitiful jousts.",
        "Jack amazed a few girls by dropping the antique onyx vase!",
        "We have just quoted on nine dozen boxes of gray lamp wicks.",
        "Jay visited back home and gazed upon a brown fox and quail.",
        "May Jo equal the fine record by solving six puzzles a week?",
        "Fred specialized in the job of making very quaint wax toys.",
        "Freight to me sixty dozen quart jars and twelve black pans.",
        "Jeb quickly drove a few extra miles on the glazed pavement.",
        "Grumpy wizards make toxic brew for the evil Queen and Jack.",
        "Verily the dark ex-Jew quit Zionism, preferring the cabala.",
        "The job of waxing linoleum frequently peeves chintzy kids.",
        "West quickly gave Bert handsome prizes for six juicy plums.",
        "Just keep examining every low bid quoted for zinc etchings.",
        "A quick movement of the enemy will jeopardize six gunboats.",
        "All questions asked by five watch experts amazed the judge.",
        "The exodus of jazzy pigeons is craved by squeamish walkers."
    )

    val urduPangrams = listOf(
        "ایک ٹیلے پر واقع مزار خواجہ فریدالدین گنج شکرؒ کے احاطہء صحن میں ذرا سی ژالہ باری چاندی کے ڈھیروں کی مثل بڑے غضب کا نظارا دیتی ہے۔",
        "ٹھنڈ میں، ایک قحط زدہ گاؤں سے گذرتے وقت ایک چڑچڑے، باأثر و فارغ شخص کو بعض جل پری نما اژدہے نظر آئے۔",
        "ژالہ باری میں ر‌ضائی کو غلط اوڑھے بیٹھی قرأة العین اور عظمٰی کے پاس گھر کے ذخیرے سے آناً فاناً ڈش میں ثابت جو، صراحی میں چائے اور پلیٹ میں زرده آیا۔"
    )

    override fun bind(binding: ItemFontBinding, item: Typeface) {
        val pangram = pangrams.random()
        binding.englishTextCaps.text = pangram.uppercase()
        binding.englishTextSmalls.text = pangram.lowercase()
        binding.urduText.text = urduPangrams.random()
        binding.englishTextCaps.setTypeface(item, viewModel.typeface.value)
        binding.urduText.setTypeface(item, viewModel.typeface.value)
        binding.englishTextSmalls.setTypeface(item, viewModel.typeface.value)
        binding.root.background = viewModel.background.value
        binding.englishTextSmalls.setTextColor(viewModel.textColor.value)
        binding.urduText.setTextColor(viewModel.textColor.value)
        binding.englishTextCaps.setTextColor(viewModel.textColor.value)
    }
}
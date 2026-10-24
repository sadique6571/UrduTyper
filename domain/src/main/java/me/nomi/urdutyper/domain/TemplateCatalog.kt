package me.nomi.urdutyper.domain

import me.nomi.urdutyper.domain.entity.Template
import me.nomi.urdutyper.domain.entity.TemplateCategory
import me.nomi.urdutyper.domain.entity.TemplateStyle

object TemplateCatalog {

    val templates: List<Template> = listOf(
        Template(
            id = "ishq_1",
            category = TemplateCategory.ISHQ,
            title = "یادوں کی روشنی",
            couplet = "تیری یادوں کی روشنی میں\nہر رات ایک نیا سویرا ہے",
            fontKey = "nastaliq",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "ishq_2",
            category = TemplateCategory.ISHQ,
            title = "دل کی دھڑکن",
            couplet = "دل نے جب سے تجھے چاہا ہے\nہر دھڑکن تیرا نام لیتی ہے",
            fontKey = "gandhara",
            style = TemplateStyle.INK_DARK
        ),
        Template(
            id = "dard_1",
            category = TemplateCategory.DARD,
            title = "تنہائی کی خاموشی",
            couplet = "تنہائی کی خاموشی میں\nدل اپنا حال سناتا ہے",
            fontKey = "aadil",
            style = TemplateStyle.DUSK
        ),
        Template(
            id = "dard_2",
            category = TemplateCategory.DARD,
            title = "مسکراہٹ میں زخم",
            couplet = "کچھ زخم ایسے ہوتے ہیں\nجو مسکراہٹ میں چھپ جاتے ہیں",
            fontKey = "nastaliq",
            style = TemplateStyle.INK_DARK
        ),
        Template(
            id = "motivation_1",
            category = TemplateCategory.MOTIVATION,
            title = "گر کر اٹھنا",
            couplet = "گر کر اٹھنا ہی جیت ہے\nرکنا کبھی ہار نہیں ہوتا",
            fontKey = "almas",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "motivation_2",
            category = TemplateCategory.MOTIVATION,
            title = "محنت کی راہ",
            couplet = "محنت کی راہ پر چلنے والے\nمنزل کو ضرور پاتے ہیں",
            fontKey = "a300",
            style = TemplateStyle.DUSK
        ),
        Template(
            id = "islamic_1",
            category = TemplateCategory.ISLAMIC,
            title = "ذکر سے سکون",
            couplet = "اللہ کے ذکر سے دل کو\nسکون اور قرار ملتا ہے",
            fontKey = "makki",
            style = TemplateStyle.INK_DARK
        ),
        Template(
            id = "islamic_2",
            category = TemplateCategory.ISLAMIC,
            title = "صبر اور رحمت",
            couplet = "صبر کرنے والوں کے ساتھ\nاللہ کی رحمت ہوتی ہے",
            fontKey = "nastaliq",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "dosti_1",
            category = TemplateCategory.DOSTI,
            title = "سچا دوست",
            couplet = "سچا دوست وہی ہوتا ہے\nجو ہر مشکل میں ساتھ دے",
            fontKey = "aadil",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "dosti_2",
            category = TemplateCategory.DOSTI,
            title = "دل کا وعدہ",
            couplet = "دوستی کا رشتہ انمول ہے\nیہ دل سے دل کا وعدہ ہے",
            fontKey = "gandhara",
            style = TemplateStyle.DUSK
        ),
        Template(
            id = "life_1",
            category = TemplateCategory.LIFE,
            title = "زندگی کا سفر",
            couplet = "زندگی ایک سفر ہے پیارے\nہر موڑ پر نیا سبق ہے",
            fontKey = "a300",
            style = TemplateStyle.INK_DARK
        ),
        Template(
            id = "life_2",
            category = TemplateCategory.LIFE,
            title = "وقت اور یادیں",
            couplet = "وقت گزر جاتا ہے خاموشی سے\nیادیں رہ جاتی ہیں دل میں",
            fontKey = "makki",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "birthday_1",
            category = TemplateCategory.BIRTHDAY,
            title = "سالگرہ مبارک",
            couplet = "سالگرہ مبارک ہو تمہیں\nخوشیاں تمہارے قدم چومیں",
            fontKey = "almas",
            style = TemplateStyle.DUSK
        ),
        Template(
            id = "birthday_2",
            category = TemplateCategory.BIRTHDAY,
            title = "خاص دن",
            couplet = "آج کا دن خاص ہے بہت\nتمہاری مسکراہٹ سلامت رہے",
            fontKey = "nastaliq",
            style = TemplateStyle.INK_DARK
        ),
        Template(
            id = "eid_ramzan_1",
            category = TemplateCategory.EID_RAMZAN,
            title = "عید کی خوشیاں",
            couplet = "عید کی خوشیاں مبارک ہوں\nہر گھر میں روشنی ہو",
            fontKey = "makki",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "eid_ramzan_2",
            category = TemplateCategory.EID_RAMZAN,
            title = "رمضان کی برکتیں",
            couplet = "رمضان کی برکتیں سب پر\nرحمت کی بارش ہو",
            fontKey = "gandhara",
            style = TemplateStyle.INK_DARK
        ),
        Template(
            id = "glow_ishq",
            category = TemplateCategory.ISHQ,
            title = "چاندنی رات",
            couplet = "چاندنی رات میں تیری یاد آئی\nدل نے پھر تیرا نام پکارا",
            fontKey = "nastaliq",
            style = TemplateStyle.DARK_GLOW
        ),
        Template(
            id = "rain_dard",
            category = TemplateCategory.DARD,
            title = "بارش کی رات",
            couplet = "بارش کی بوندوں میں چھپا ہے\nمیرا ہر ادھورا خواب",
            fontKey = "shekastah",
            style = TemplateStyle.RAIN_GLASS
        ),
        Template(
            id = "autumn_life",
            category = TemplateCategory.LIFE,
            title = "خزاں کا موسم",
            couplet = "خزاں کے پتوں کی طرح\nزندگی بھی بدلتی رہتی ہے",
            fontKey = "alvi",
            style = TemplateStyle.AUTUMN
        ),
        Template(
            id = "paper_dosti",
            category = TemplateCategory.DOSTI,
            title = "پرانا خط",
            couplet = "پرانے خط کی خوشبو میں\nدوستی کی یاد بسی ہے",
            fontKey = "pencil",
            style = TemplateStyle.PAPER
        ),
        Template(
            id = "truck_birthday",
            category = TemplateCategory.BIRTHDAY,
            title = "رنگ برنگ دن",
            couplet = "رنگوں سے بھرا یہ دن تمہارا\nخوشیاں لے کر آیا ہے",
            fontKey = "gandhara",
            style = TemplateStyle.TRUCK_ART
        ),
        Template(
            id = "framed_islamic",
            category = TemplateCategory.ISLAMIC,
            title = "نور کی روشنی",
            couplet = "اللہ کے نور سے روشن ہے\nہر دل کا راستہ",
            fontKey = "makki",
            style = TemplateStyle.FRAMED
        )
    )
}

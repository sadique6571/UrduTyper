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
        ),
        Template(
            id = "wedding_1",
            category = TemplateCategory.WEDDING,
            title = "شادی مبارک",
            couplet = "شادی مبارک ہو تمہیں\nخوشیاں تمہارے سنگ رہیں",
            fontKey = "gandhara",
            style = TemplateStyle.TRUCK_ART
        ),
        Template(
            id = "wedding_2",
            category = TemplateCategory.WEDDING,
            title = "نیا سفر",
            couplet = "نیا سفر مبارک ہو تمہیں\nمحبتوں کا یہ دن رہے",
            fontKey = "nastaliq",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "national_1",
            category = TemplateCategory.NATIONAL_DAY,
            title = "پیارا وطن",
            couplet = "پاکستان ہمارا پیارا وطن\nاس کی محبت دل میں ہے",
            fontKey = "alvi",
            style = TemplateStyle.DARK_GLOW
        ),
        Template(
            id = "national_2",
            category = TemplateCategory.NATIONAL_DAY,
            title = "یوم آزادی",
            couplet = "یوم آزادی مبارک ہو\nہر دل میں جشن کا سماں ہے",
            fontKey = "makki",
            style = TemplateStyle.GRADIENT
        ),
        Template(
            id = "greetings_1",
            category = TemplateCategory.GREETINGS,
            title = "دعا",
            couplet = "تمہارے لیے دعا ہے یہی\nخوشیاں تمہیں ملیں سدا",
            fontKey = "pencil",
            style = TemplateStyle.PAPER
        ),
        Template(
            id = "greetings_2",
            category = TemplateCategory.GREETINGS,
            title = "مبارکباد",
            couplet = "مبارکباد تمہیں اس خوشی کی\nہر دن تمہارا روشن رہے",
            fontKey = "almas",
            style = TemplateStyle.DUSK
        ),
        Template(
            id = "demo_glow_ishq",
            category = TemplateCategory.ISHQ,
            title = "یادوں کی روشنی",
            couplet = "یادوں کی روشنی میں کھویا ہوا\nدل آج بھی تیرا ہی ہوا",
            fontKey = "nastaliq",
            style = TemplateStyle.DEMO_GLOW
        ),
        Template(
            id = "demo_rain_dard",
            category = TemplateCategory.DARD,
            title = "بارش کی یاد",
            couplet = "بارش کی بوندوں میں تیری یاد\nبھیگی ہوئی راتوں کی فریاد",
            fontKey = "nastaliq",
            style = TemplateStyle.DEMO_RAIN
        ),
        Template(
            id = "demo_autumn_life",
            category = TemplateCategory.LIFE,
            title = "خزاں کی شام",
            couplet = "خزاں کے پتوں میں چھپی کہانی\nزندگی کی ہر شام سہانی",
            fontKey = "nastaliq",
            style = TemplateStyle.DEMO_AUTUMN
        ),
        Template(
            id = "demo_paper_dosti",
            category = TemplateCategory.DOSTI,
            title = "کاغذ کی بات",
            couplet = "کاغذ پر لکھی بات پرانی\nدوستی کی یاد ہے سہانی",
            fontKey = "nastaliq",
            style = TemplateStyle.DEMO_PAPER
        ),
        Template(
            id = "pic_glow_ishq",
            category = TemplateCategory.ISHQ,
            title = "نیلی روشنی",
            couplet = "نیلی روشنی میں تیرا چہرہ\nدل کو آج بھی یاد آتا ہے",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_GLOW
        ),
        Template(
            id = "pic_rain_dard",
            category = TemplateCategory.DARD,
            title = "شیشے پر بارش",
            couplet = "شیشے پر بارش کی بوندیں\nتیری یاد دلاتی ہیں",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_RAIN
        ),
        Template(
            id = "pic_autumn_life",
            category = TemplateCategory.LIFE,
            title = "خزاں کے رنگ",
            couplet = "خزاں کے رنگوں میں چھپی ہے\nزندگی کی اک نئی صبح",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_AUTUMN
        ),
        Template(
            id = "pic_paper_dosti",
            category = TemplateCategory.DOSTI,
            title = "کاغذ کی یاد",
            couplet = "کاغذ پر بکھری سیاہی میں\nدوستی کی کہانی بسی ہے",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_PAPER
        ),
        Template(
            id = "pic_ink_ishq",
            category = TemplateCategory.ISHQ,
            title = "سیاہی کے رنگ",
            couplet = "سیاہی کے رنگوں میں چھپی ہے\nمحبت کی اک نئی کہانی",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_INK
        ),
        Template(
            id = "pic_truck_birthday",
            category = TemplateCategory.BIRTHDAY,
            title = "ٹرک آرٹ دن",
            couplet = "ٹرک آرٹ کے رنگوں جیسا\nیہ دن بھی رنگین ہو تمہارا",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_TRUCK
        ),
        Template(
            id = "pic_framed_islamic",
            category = TemplateCategory.ISLAMIC,
            title = "خاموش دعا",
            couplet = "خاموشی میں چھپی دعا\nدل کو سکون دیتی ہے",
            fontKey = "nastaliq",
            style = TemplateStyle.PIC_FRAMED
        )
    )
}

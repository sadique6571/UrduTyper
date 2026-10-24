package me.nomi.urdutyper.domain.entity

data class Template(
    val id: String,
    val category: TemplateCategory,
    val title: String,
    val couplet: String,
    val fontKey: String,
    val style: TemplateStyle
)

enum class TemplateCategory(val urduName: String) {
    ISHQ("عشق"),
    DARD("درد"),
    MOTIVATION("موٹیویشن"),
    ISLAMIC("اسلامی"),
    DOSTI("دوستی"),
    LIFE("زندگی"),
    BIRTHDAY("سالگرہ"),
    EID_RAMZAN("عید و رمضان"),
    WEDDING("شادی"),
    NATIONAL_DAY("قومی دن"),
    GREETINGS("مبارکباد")
}

enum class TemplateStyle {
    GRADIENT,
    INK_DARK,
    DUSK,
    DARK_GLOW,
    RAIN_GLASS,
    AUTUMN,
    PAPER,
    TRUCK_ART,
    FRAMED
}

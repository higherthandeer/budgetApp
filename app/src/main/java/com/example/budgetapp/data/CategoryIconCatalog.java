package com.example.budgetapp.data;

import java.util.*;

/**
 * Stable persisted keys for the bundled Material Symbols Rounded selection.
 */
public final class CategoryIconCatalog {
    private CategoryIconCatalog() {
    }

    public static final class Icon {
        public final String key, label, group;

        Icon(String key, String label, String group) {
            this.key = key;
            this.label = label;
            this.group = group;
        }
    }

    public static final List<Icon> ALL = Collections.unmodifiableList(Arrays.asList(
            new Icon("restaurant", "餐饮", "餐饮美食"),
            new Icon("nutrition", "水果", "餐饮美食"),
            new Icon("local_cafe", "咖啡", "餐饮美食"),
            new Icon("local_bar", "饮品", "餐饮美食"),
            new Icon("breakfast_dining", "早餐", "餐饮美食"),
            new Icon("lunch_dining", "午餐", "餐饮美食"),
            new Icon("dinner_dining", "晚餐", "餐饮美食"),
            new Icon("bakery_dining", "烘焙", "餐饮美食"),
            new Icon("ramen_dining", "面食", "餐饮美食"),
            new Icon("icecream", "冰淇淋", "餐饮美食"),
            new Icon("local_pizza", "披萨", "餐饮美食"),
            new Icon("shopping_cart", "超市", "购物消费"),
            new Icon("shopping_bag", "购物", "购物消费"),
            new Icon("checkroom", "衣物", "购物消费"),
            new Icon("diamond", "珠宝", "购物消费"),
            new Icon("watch", "手表", "购物消费"),
            new Icon("face", "美妆", "购物消费"),
            new Icon("loyalty", "会员卡", "购物消费"),
            new Icon("storefront", "商店", "购物消费"),
            new Icon("sell", "折扣", "购物消费"),
            new Icon("inventory_2", "日用品", "购物消费"),
            new Icon("train", "火车", "交通出行"),
            new Icon("directions_bus", "公交", "交通出行"),
            new Icon("directions_car", "汽车", "交通出行"),
            new Icon("two_wheeler", "两轮车", "交通出行"),
            new Icon("flight", "飞机", "交通出行"),
            new Icon("local_gas_station", "加油", "交通出行"),
            new Icon("subway", "地铁", "交通出行"),
            new Icon("local_taxi", "出租车", "交通出行"),
            new Icon("pedal_bike", "自行车", "交通出行"),
            new Icon("local_parking", "停车", "交通出行"),
            new Icon("home", "居住", "居家生活"),
            new Icon("apartment", "房屋", "居家生活"),
            new Icon("chair", "家具", "居家生活"),
            new Icon("bed", "住宿", "居家生活"),
            new Icon("water_drop", "用水", "居家生活"),
            new Icon("electric_bolt", "用电", "居家生活"),
            new Icon("kitchen", "厨房", "居家生活"),
            new Icon("local_laundry_service", "洗衣", "居家生活"),
            new Icon("cleaning_services", "保洁", "居家生活"),
            new Icon("build", "维修", "居家生活"),
            new Icon("phone_android", "手机", "数码通讯"),
            new Icon("wifi", "网络", "数码通讯"),
            new Icon("computer", "电脑", "数码通讯"),
            new Icon("laptop_mac", "笔记本", "数码通讯"),
            new Icon("headphones", "耳机", "数码通讯"),
            new Icon("photo_camera", "摄影", "数码通讯"),
            new Icon("print", "打印", "数码通讯"),
            new Icon("smart_display", "视频", "数码通讯"),
            new Icon("router", "路由器", "数码通讯"),
            new Icon("devices_other", "数码配件", "数码通讯"),
            new Icon("school", "学习", "学习办公"),
            new Icon("menu_book", "书籍", "学习办公"),
            new Icon("edit", "文具", "学习办公"),
            new Icon("draw", "绘画", "学习办公"),
            new Icon("calculate", "计算", "学习办公"),
            new Icon("science", "科研", "学习办公"),
            new Icon("translate", "外语", "学习办公"),
            new Icon("backpack", "书包", "学习办公"),
            new Icon("work", "工作", "学习办公"),
            new Icon("folder", "文件", "学习办公"),
            new Icon("sports_esports", "游戏", "娱乐休闲"),
            new Icon("movie", "电影", "娱乐休闲"),
            new Icon("music_note", "音乐", "娱乐休闲"),
            new Icon("theater_comedy", "演出", "娱乐休闲"),
            new Icon("confirmation_number", "门票", "娱乐休闲"),
            new Icon("celebration", "聚会", "娱乐休闲"),
            new Icon("casino", "桌游", "娱乐休闲"),
            new Icon("sports_bar", "酒吧", "娱乐休闲"),
            new Icon("live_tv", "电视", "娱乐休闲"),
            new Icon("piano", "乐器", "娱乐休闲"),
            new Icon("fitness_center", "健身", "运动户外"),
            new Icon("directions_run", "跑步", "运动户外"),
            new Icon("hiking", "徒步", "运动户外"),
            new Icon("sports_soccer", "足球", "运动户外"),
            new Icon("sports_basketball", "篮球", "运动户外"),
            new Icon("sports_tennis", "网球", "运动户外"),
            new Icon("pool", "游泳", "运动户外"),
            new Icon("sports_volleyball", "排球", "运动户外"),
            new Icon("self_improvement", "瑜伽", "运动户外"),
            new Icon("camping", "露营", "运动户外"),
            new Icon("medical_services", "医疗", "医疗健康"),
            new Icon("favorite", "健康", "医疗健康"),
            new Icon("local_hospital", "医院", "医疗健康"),
            new Icon("local_pharmacy", "药店", "医疗健康"),
            new Icon("medication", "药品", "医疗健康"),
            new Icon("healing", "护理", "医疗健康"),
            new Icon("health_and_safety", "保险", "医疗健康"),
            new Icon("monitor_heart", "体检", "医疗健康"),
            new Icon("visibility", "眼科", "医疗健康"),
            new Icon("dentistry", "牙科", "医疗健康"),
            new Icon("pets", "宠物", "家庭宠物"),
            new Icon("child_care", "育儿", "家庭宠物"),
            new Icon("stroller", "婴儿车", "家庭宠物"),
            new Icon("toys", "玩具", "家庭宠物"),
            new Icon("elderly", "养老", "家庭宠物"),
            new Icon("family_restroom", "家庭", "家庭宠物"),
            new Icon("cruelty_free", "宠物用品", "家庭宠物"),
            new Icon("yard", "园艺", "家庭宠物"),
            new Icon("spa", "美容", "家庭宠物"),
            new Icon("volunteer_activism", "公益", "家庭宠物"),
            new Icon("paid", "收入", "收入财务"),
            new Icon("account_balance_wallet", "钱包", "收入财务"),
            new Icon("savings", "储蓄", "收入财务"),
            new Icon("redeem", "礼物", "收入财务"),
            new Icon("trending_up", "理财", "收入财务"),
            new Icon("account_balance", "银行", "收入财务"),
            new Icon("credit_card", "信用卡", "收入财务"),
            new Icon("receipt_long", "账单", "收入财务"),
            new Icon("currency_exchange", "外汇", "收入财务"),
            new Icon("attach_money", "现金", "收入财务"),
            new Icon("luggage", "行李", "旅行服务"),
            new Icon("hotel", "酒店", "旅行服务"),
            new Icon("beach_access", "度假", "旅行服务"),
            new Icon("map", "导航", "旅行服务"),
            new Icon("attractions", "景点", "旅行服务"),
            new Icon("local_shipping", "快递", "旅行服务"),
            new Icon("local_post_office", "邮寄", "旅行服务"),
            new Icon("handyman", "家政", "旅行服务"),
            new Icon("security", "安保", "旅行服务"),
            new Icon("category", "其他", "旅行服务")));

    public static boolean contains(String key) {
        for (Icon icon : ALL) if (icon.key.equals(key)) return true;
        return false;
    }

    public static String label(String key) {
        for (Icon icon : ALL) if (icon.key.equals(key)) return icon.label;
        return "其他";
    }

    public static String defaultForName(String name) {
        if (name == null) return "category";
        switch (name) {
            case "餐饮":
                return "restaurant";
            case "交通":
                return "train";
            case "工资":
                return "paid";
            case "居住":
                return "home";
            case "健康":
                return "favorite";
            case "娱乐":
                return "sports_esports";
            case "日常":
                return "shopping_bag";
            default:
                return "category";
        }
    }
}

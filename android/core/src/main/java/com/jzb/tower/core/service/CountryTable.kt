package com.jzb.tower.core.service

import com.jzb.tower.core.model.CountryEntry
import com.jzb.tower.core.model.ProxyNode
import java.util.Locale

/** Generated country data ported from Tower/Services/CountryTable.swift and NodeRegionResolver.swift. */
object CountryTable {
    private data class Row(val entry: CountryEntry, val aliases: List<String>, val codes: List<String>)

    fun flag(code: String): String = code.uppercase(Locale.ROOT).takeIf { it.matches(Regex("[A-Z]{2}")) }
        ?.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }?.joinToString("").orEmpty()

    private fun row(code: String, chinese: String, english: String, latitude: Double, longitude: Double,
                    aliases: List<String>, codes: List<String>) =
        Row(CountryEntry(code, chinese, english, flag(code), latitude, longitude), aliases, codes)

    private val rows = listOf(
        row("AD", "安道尔", "Andorra", 42.5063, 1.5218, listOf("andorra"), listOf("AND")),
        row("AE", "阿联酋", "United Arab Emirates", 25.2048, 55.2708, listOf("united arab emirates", "阿拉伯联合酋长国", "阿拉伯聯合大公國", "阿拉伯联合酋长国", "阿联酋", "迪拜", "uae", "dubai", "🇦🇪"), listOf("ARE")),
        row("AF", "阿富汗", "Afghanistan", 34.1643, 66.4966, listOf("afghanistan", "islamic state of afghanistan", "阿富汗"), listOf("AFG")),
        row("AL", "阿尔巴尼亚", "Albania", 40.6549, 20.1138, listOf("albania", "republic of albania", "阿尔巴尼亚", "阿爾巴尼亞"), listOf("ALB")),
        row("AM", "亚美尼亚", "Armenia", 40.4591, 44.8006, listOf("armenia", "republic of armenia", "亚美尼亚", "亞美尼亞"), listOf("ARM")),
        row("AO", "安哥拉", "Angola", -12.1828, 17.9842, listOf("angola", "people's republic of angola", "安哥拉"), listOf("AGO")),
        row("AQ", "南极洲", "Antarctica", -79.8432, 35.8855, listOf("antarctica", "南极洲", "南極洲"), listOf("ATA")),
        row("AR", "阿根廷", "Argentina", -33.5012, -64.1733, listOf("argentina", "argentine republic", "阿根廷"), listOf("ARG")),
        row("AT", "奥地利", "Austria", 47.5189, 14.1305, listOf("austria", "republic of austria", "奥地利", "奧地利"), listOf("AUT")),
        row("AU", "澳大利亚", "Australia", -33.8688, 151.2093, listOf("australia", "commonwealth of australia", "澳大利亚", "澳大利亞", "澳大利亚", "澳大利亚", "澳洲", "悉尼", "墨尔本", "australia", "sydney", "melbourne", "🇦🇺"), listOf("AUS")),
        row("AZ", "阿塞拜疆", "Azerbaijan", 40.4024, 47.2110, listOf("azerbaijan", "republic of azerbaijan", "阿塞拜疆", "亞塞拜然"), listOf("AZE")),
        row("BA", "波斯尼亚和黑塞哥维那", "Bosnia and Herz.", 44.0911, 18.0684, listOf("bosnia and herz.", "bosnia and herzegovina", "波斯尼亚和黑塞哥维那", "波士尼亞與赫塞哥維納"), listOf("BIH")),
        row("BD", "孟加拉国", "Bangladesh", 24.2150, 89.6850, listOf("bangladesh", "people's republic of bangladesh", "孟加拉国", "孟加拉"), listOf("BGD")),
        row("BE", "比利时", "Belgium", 50.7854, 4.8004, listOf("belgium", "kingdom of belgium", "比利时", "比利時"), listOf("BEL")),
        row("BF", "布基纳法索", "Burkina Faso", 12.6730, -1.3639, listOf("burkina faso", "布基纳法索", "布基納法索"), listOf("BFA")),
        row("BG", "保加利亚", "Bulgaria", 42.5088, 25.1571, listOf("bulgaria", "republic of bulgaria", "保加利亚", "保加利亞"), listOf("BGR")),
        row("BH", "巴林", "Bahrain", 26.0667, 50.5577, listOf("bahrain"), listOf("BHR")),
        row("BI", "布隆迪", "Burundi", -3.3328, 29.9171, listOf("burundi", "republic of burundi", "布隆迪", "蒲隆地"), listOf("BDI")),
        row("BJ", "贝宁", "Benin", 10.3248, 2.3520, listOf("benin", "republic of benin", "贝宁", "貝南"), listOf("BEN")),
        row("BN", "文莱", "Brunei", 4.4483, 114.5519, listOf("brunei", "brunei darussalam", "negara brunei darussalam", "文莱", "汶萊"), listOf("BRN")),
        row("BO", "玻利维亚", "Bolivia", -16.6660, -64.5934, listOf("bolivia", "plurinational state of bolivia", "玻利维亚", "玻利維亞"), listOf("BOL")),
        row("BR", "巴西", "Brazil", -23.5505, -46.6333, listOf("brazil", "federative republic of brazil", "巴西", "巴西", "巴西", "圣保罗", "brazil", "sao paulo", "são paulo", "🇧🇷"), listOf("BRA")),
        row("BS", "巴哈马", "Bahamas", 26.4018, -77.1467, listOf("bahamas", "the bahamas", "bahamas, the", "commonwealth of the bahamas", "巴哈马", "巴哈馬"), listOf("BHS")),
        row("BT", "不丹", "Bhutan", 27.5367, 90.0403, listOf("bhutan", "kingdom of bhutan", "不丹"), listOf("BTN")),
        row("BW", "博茨瓦纳", "Botswana", -22.1026, 24.1792, listOf("botswana", "republic of botswana", "博茨瓦纳", "波札那"), listOf("BWA")),
        row("BY", "白俄罗斯", "Belarus", 53.8219, 28.4177, listOf("belarus", "republic of belarus", "白俄罗斯", "白俄羅斯"), listOf("BLR")),
        row("BZ", "伯利兹", "Belize", 17.2021, -88.7130, listOf("belize", "伯利兹", "貝里斯"), listOf("BLZ")),
        row("CA", "加拿大", "Canada", 43.6532, -79.3832, listOf("canada", "加拿大", "加拿大", "加拿大", "多伦多", "温哥华", "canada", "toronto", "vancouver", "🇨🇦"), listOf("CAN")),
        row("CD", "刚果民主共和国", "Dem. Rep. Congo", -1.8582, 23.4588, listOf("dem. rep. congo", "democratic republic of the congo", "congo, democratic republic of the", "congo, dem. rep.", "刚果民主共和国", "剛果民主共和國"), listOf("COD")),
        row("CF", "中非共和国", "Central African Rep.", 6.9897, 20.9069, listOf("central african rep.", "central african republic", "中非共和国", "中非共和國"), listOf("CAF")),
        row("CG", "刚果共和国", "Congo", 0.1423, 15.9005, listOf("congo", "republic of the congo", "congo, republic of the", "congo, rep.", "刚果共和国", "剛果共和國"), listOf("COG")),
        row("CH", "瑞士", "Switzerland", 46.7191, 7.4640, listOf("switzerland", "swiss confederation", "瑞士"), listOf("CHE")),
        row("CI", "科特迪瓦", "Côte d'Ivoire", 7.4914, -5.5686, listOf("côte d'ivoire", "ivory coast", "cote d'ivoire", "republic of ivory coast", "科特迪瓦", "象牙海岸"), listOf("CIV")),
        row("CL", "智利", "Chile", -38.1518, -72.3189, listOf("chile", "republic of chile", "智利"), listOf("CHL")),
        row("CM", "喀麦隆", "Cameroon", 4.5850, 12.4735, listOf("cameroon", "republic of cameroon", "喀麦隆", "喀麥隆"), listOf("CMR")),
        row("CN", "中华人民共和国", "China", 32.4982, 106.3373, listOf("china", "people's republic of china", "中华人民共和国", "中華人民共和國"), listOf("CHN")),
        row("CO", "哥伦比亚", "Colombia", 3.3731, -73.1743, listOf("colombia", "republic of colombia", "哥伦比亚", "哥倫比亞"), listOf("COL")),
        row("CR", "哥斯达黎加", "Costa Rica", 10.0651, -84.0779, listOf("costa rica", "republic of costa rica", "哥斯达黎加", "哥斯大黎加"), listOf("CRI")),
        row("CU", "古巴", "Cuba", 21.3340, -77.9759, listOf("cuba", "republic of cuba", "古巴"), listOf("CUB")),
        row("CY", "塞浦路斯", "Cyprus", 34.9133, 33.0842, listOf("cyprus", "republic of cyprus", "塞浦路斯", "賽普勒斯"), listOf("CYP")),
        row("CZ", "捷克", "Czechia", 49.8824, 15.3776, listOf("czechia", "czech republic", "česko", "捷克", "捷克共和國"), listOf("CZE")),
        row("DE", "德国", "Germany", 50.1109, 8.6821, listOf("germany", "federal republic of germany", "德国", "德國", "德国", "德国", "法兰克福", "柏林", "germany", "frankfurt", "berlin", "🇩🇪"), listOf("DEU")),
        row("DJ", "吉布提", "Djibouti", 11.9763, 42.4988, listOf("djibouti", "republic of djibouti", "吉布提", "吉布地"), listOf("DJI")),
        row("DK", "丹麦", "Denmark", 55.9670, 9.0182, listOf("denmark", "kingdom of denmark", "丹麦", "丹麥"), listOf("DNK")),
        row("DO", "多米尼加", "Dominican Rep.", 19.1041, -70.6540, listOf("dominican rep.", "dominican republic", "多米尼加", "多明尼加"), listOf("DOM")),
        row("DZ", "阿尔及利亚", "Algeria", 27.3974, 2.8082, listOf("algeria", "people's democratic republic of algeria", "阿尔及利亚", "阿爾及利亞"), listOf("DZA")),
        row("EC", "厄瓜多尔", "Ecuador", -1.2591, -78.1884, listOf("ecuador", "republic of ecuador", "厄瓜多尔", "厄瓜多爾"), listOf("ECU")),
        row("EE", "爱沙尼亚", "Estonia", 58.7249, 25.8671, listOf("estonia", "republic of estonia", "爱沙尼亚", "愛沙尼亞"), listOf("EST")),
        row("EG", "埃及", "Egypt", 26.1862, 29.4458, listOf("egypt", "egypt, arab rep.", "arab republic of egypt", "埃及"), listOf("EGY")),
        row("EH", "西撒哈拉", "W. Sahara", 23.9676, -12.6303, listOf("w. sahara", "western sahara", "sahrawi arab democratic republic", "西撒哈拉"), listOf("ESH", "SAH")),
        row("ER", "厄立特里亚", "Eritrea", 15.7874, 38.2856, listOf("eritrea", "state of eritrea", "厄立特里亚", "厄利垂亞"), listOf("ERI")),
        row("ES", "西班牙", "Spain", 40.0910, -3.4647, listOf("spain", "kingdom of spain", "西班牙"), listOf("ESP")),
        row("ET", "埃塞俄比亚", "Ethiopia", 8.0328, 39.0886, listOf("ethiopia", "federal democratic republic of ethiopia", "埃塞俄比亚", "衣索比亞"), listOf("ETH")),
        row("FI", "芬兰", "Finland", 63.2524, 27.2764, listOf("finland", "republic of finland", "芬兰", "芬蘭"), listOf("FIN")),
        row("FJ", "斐济", "Fiji", -17.8261, 177.9754, listOf("fiji", "republic of fiji", "斐济", "斐濟"), listOf("FJI")),
        row("FK", "福克兰群岛", "Falkland Is.", -51.6089, -58.7386, listOf("falkland is.", "falkland islands / malvinas", "falkland islands", "falkland islands (islas malvinas)", "islas malvinas", "福克兰群岛", "福克蘭群島"), listOf("FLK")),
        row("FR", "法国", "France", 48.8566, 2.3522, listOf("france", "french republic", "法国", "法國", "法国", "法国", "巴黎", "france", "paris", "🇫🇷"), listOf("FRA")),
        row("GA", "加蓬", "Gabon", -0.4377, 11.8359, listOf("gabon", "gabonese republic", "加蓬", "加彭"), listOf("GAB")),
        row("GB", "英国", "United Kingdom", 51.5074, -0.1278, listOf("united kingdom", "united kingdom of great britain and northern ireland", "英国", "英國", "英国", "英国", "伦敦", "英格兰", "苏格兰", "united kingdom", "great britain", "britain", "england", "scotland", "wales", "london", "🇬🇧"), listOf("GBR")),
        row("GE", "格鲁吉亚", "Georgia", 41.8701, 43.7357, listOf("georgia", "格鲁吉亚", "喬治亞"), listOf("GEO")),
        row("GH", "加纳", "Ghana", 7.7176, -1.0369, listOf("ghana", "republic of ghana", "加纳", "迦納"), listOf("GHA")),
        row("GI", "直布罗陀", "Gibraltar", 36.1408, -5.3536, listOf("gibraltar"), listOf("GIB")),
        row("GL", "格陵兰", "Greenland", 74.3194, -39.3353, listOf("greenland", "格陵兰", "格陵蘭"), listOf("GRL")),
        row("GM", "冈比亚", "Gambia", 13.6417, -14.9983, listOf("gambia", "the gambia", "gambia, the", "republic of the gambia", "冈比亚", "甘比亞"), listOf("GMB")),
        row("GN", "几内亚", "Guinea", 10.6185, -10.0164, listOf("guinea", "republic of guinea", "几内亚", "幾內亞"), listOf("GIN")),
        row("GQ", "赤道几内亚", "Eq. Guinea", 2.3330, 8.9902, listOf("eq. guinea", "equatorial guinea", "republic of equatorial guinea", "赤道几内亚", "赤道幾內亞"), listOf("GNQ")),
        row("GR", "希腊", "Greece", 39.4928, 21.7257, listOf("greece", "hellenic republic", "希腊", "希臘"), listOf("GRC")),
        row("GT", "危地马拉", "Guatemala", 14.9821, -90.4971, listOf("guatemala", "republic of guatemala", "危地马拉", "瓜地馬拉"), listOf("GTM")),
        row("GW", "几内亚比绍", "Guinea-Bissau", 12.1637, -14.5241, listOf("guinea-bissau", "republic of guinea-bissau", "几内亚比绍", "幾內亞比索"), listOf("GNB")),
        row("GY", "圭亚那", "Guyana", 5.1243, -58.9426, listOf("guyana", "co-operative republic of guyana", "圭亚那", "圭亞那"), listOf("GUY")),
        row("HK", "香港", "Hong Kong", 22.3193, 114.1694, listOf("hong kong", "hongkong", "香港", "香港", "hong kong", "hongkong", "🇭🇰"), listOf("HKG")),
        row("HN", "洪都拉斯", "Honduras", 14.7948, -86.8876, listOf("honduras", "republic of honduras", "洪都拉斯", "宏都拉斯"), listOf("HND")),
        row("HR", "克罗地亚", "Croatia", 45.8058, 16.3724, listOf("croatia", "republic of croatia", "克罗地亚", "克羅地亞"), listOf("HRV")),
        row("HT", "海地", "Haiti", 19.2638, -72.2241, listOf("haiti", "republic of haiti", "海地"), listOf("HTI")),
        row("HU", "匈牙利", "Hungary", 47.0868, 19.4479, listOf("hungary", "republic of hungary", "匈牙利"), listOf("HUN")),
        row("ID", "印度尼西亚", "Indonesia", -0.9544, 101.8929, listOf("indonesia", "republic of indonesia", "印度尼西亚", "印度尼西亞"), listOf("IDN")),
        row("IE", "爱尔兰", "Ireland", 53.0787, -7.7986, listOf("ireland", "爱尔兰", "愛爾蘭"), listOf("IRL")),
        row("IL", "以色列", "Israel", 30.9111, 34.8479, listOf("israel", "state of israel", "以色列"), listOf("ISR")),
        row("IN", "印度", "India", 19.0760, 72.8777, listOf("india", "republic of india", "印度", "印度", "印度", "孟买", "班加罗尔", "india", "mumbai", "bangalore", "🇮🇳"), listOf("IND")),
        row("IQ", "伊拉克", "Iraq", 33.0940, 43.2618, listOf("iraq", "republic of iraq", "伊拉克"), listOf("IRQ")),
        row("IR", "伊朗", "Iran", 32.1662, 54.9315, listOf("iran", "iran, islamic rep.", "islamic republic of iran", "伊朗"), listOf("IRN")),
        row("IS", "冰岛", "Iceland", 64.7793, -18.6737, listOf("iceland", "republic of iceland", "冰岛", "冰島"), listOf("ISL")),
        row("IT", "意大利", "Italy", 44.7325, 11.0769, listOf("italy", "italian republic", "意大利", "義大利"), listOf("ITA")),
        row("JM", "牙买加", "Jamaica", 18.1371, -77.3188, listOf("jamaica", "牙买加", "牙買加"), listOf("JAM")),
        row("JO", "约旦", "Jordan", 30.8050, 36.3760, listOf("jordan", "hashemite kingdom of jordan", "约旦", "約旦"), listOf("JOR")),
        row("JP", "日本", "Japan", 35.6762, 139.6503, listOf("japan", "日本", "日本", "日本", "东京", "大阪", "tokyo", "osaka", "japan", "🇯🇵"), listOf("JPN")),
        row("KE", "肯尼亚", "Kenya", 0.5490, 37.9076, listOf("kenya", "republic of kenya", "肯尼亚", "肯亞"), listOf("KEN")),
        row("KG", "吉尔吉斯斯坦", "Kyrgyzstan", 41.6685, 74.5326, listOf("kyrgyzstan", "kyrgyz republic", "吉尔吉斯斯坦", "吉爾吉斯"), listOf("KGZ")),
        row("KH", "柬埔寨", "Cambodia", 12.6476, 104.5049, listOf("cambodia", "kingdom of cambodia", "柬埔寨"), listOf("KHM")),
        row("KP", "朝鲜民主主义人民共和国", "North Korea", 39.8853, 126.4445, listOf("north korea", "dem. rep. korea", "korea, north", "korea, dem. rep.", "democratic people's republic of korea", "朝鲜民主主义人民共和国", "朝鮮民主主義人民共和國"), listOf("PRK")),
        row("KR", "韩国", "South Korea", 37.5665, 126.9780, listOf("south korea", "republic of korea", "korea, south", "korea, rep.", "大韩民国", "大韓民國", "大韩民国", "韩国", "首尔", "南韩", "korea", "south korea", "seoul", "🇰🇷"), listOf("KOR")),
        row("KW", "科威特", "Kuwait", 29.4136, 47.3140, listOf("kuwait", "state of kuwait", "科威特"), listOf("KWT")),
        row("KZ", "哈萨克斯坦", "Kazakhstan", 49.0541, 68.6855, listOf("kazakhstan", "republic of kazakhstan", "哈萨克斯坦", "哈薩克"), listOf("KAZ")),
        row("LA", "老挝", "Laos", 19.4318, 102.5339, listOf("laos", "lao pdr", "lao people's democratic republic", "老挝", "寮國"), listOf("LAO")),
        row("LB", "黎巴嫩", "Lebanon", 34.1334, 35.9929, listOf("lebanon", "lebanese republic", "黎巴嫩"), listOf("LBN")),
        row("LI", "列支敦士登", "Liechtenstein", 47.1660, 9.5554, listOf("liechtenstein"), listOf("LIE")),
        row("LK", "斯里兰卡", "Sri Lanka", 7.5811, 80.7048, listOf("sri lanka", "democratic socialist republic of sri lanka", "斯里兰卡", "斯里蘭卡"), listOf("LKA")),
        row("LR", "利比里亚", "Liberia", 6.4472, -9.4604, listOf("liberia", "republic of liberia", "利比里亚", "賴比瑞亞"), listOf("LBR")),
        row("LS", "莱索托", "Lesotho", -29.4802, 28.2466, listOf("lesotho", "kingdom of lesotho", "莱索托", "賴索托"), listOf("LSO")),
        row("LT", "立陶宛", "Lithuania", 55.1037, 24.0899, listOf("lithuania", "republic of lithuania", "立陶宛"), listOf("LTU")),
        row("LU", "卢森堡", "Luxembourg", 49.7337, 6.0776, listOf("luxembourg", "grand duchy of luxembourg", "卢森堡", "盧森堡"), listOf("LUX")),
        row("LV", "拉脱维亚", "Latvia", 57.0669, 25.4587, listOf("latvia", "republic of latvia", "拉脱维亚", "拉脫維亞"), listOf("LVA")),
        row("LY", "利比亚", "Libya", 26.6389, 18.0110, listOf("libya", "利比亚", "利比亞"), listOf("LBY")),
        row("MA", "摩洛哥", "Morocco", 31.6507, -7.1873, listOf("morocco", "kingdom of morocco", "摩洛哥"), listOf("MAR")),
        row("MC", "摩纳哥", "Monaco", 43.7384, 7.4246, listOf("monaco"), listOf("MCO")),
        row("MD", "摩尔多瓦", "Moldova", 47.4350, 28.4879, listOf("moldova", "republic of moldova", "摩尔多瓦", "摩爾多瓦"), listOf("MDA")),
        row("ME", "黑山", "Montenegro", 42.8031, 19.1437, listOf("montenegro", "黑山", "蒙特內哥羅"), listOf("MNE")),
        row("MG", "马达加斯加", "Madagascar", -18.6283, 46.7042, listOf("madagascar", "republic of madagascar", "马达加斯加", "馬達加斯加"), listOf("MDG")),
        row("MK", "北马其顿", "North Macedonia", 41.5582, 21.5558, listOf("north macedonia", "republic of north macedonia", "北马其顿", "北馬其頓"), listOf("MKD")),
        row("ML", "马里", "Mali", 18.6927, -2.0385, listOf("mali", "republic of mali", "马里", "馬利共和國"), listOf("MLI")),
        row("MM", "缅甸", "Myanmar", 21.5739, 95.8045, listOf("myanmar", "burma", "republic of the union of myanmar", "缅甸", "緬甸"), listOf("MMR")),
        row("MN", "蒙古国", "Mongolia", 45.9975, 104.1504, listOf("mongolia", "蒙古国", "蒙古國"), listOf("MNG")),
        row("MO", "澳门", "Macau", 22.1987, 113.5439, listOf("macau", "macao"), listOf("MAC")),
        row("MR", "毛里塔尼亚", "Mauritania", 19.5871, -9.7403, listOf("mauritania", "islamic republic of mauritania", "毛里塔尼亚", "茅利塔尼亞"), listOf("MRT")),
        row("MT", "马耳他", "Malta", 35.9375, 14.3754, listOf("malta"), listOf("MLT")),
        row("MU", "毛里求斯", "Mauritius", -20.3484, 57.5522, listOf("mauritius"), listOf("MUS")),
        row("MW", "马拉维", "Malawi", -13.3867, 33.6081, listOf("malawi", "republic of malawi", "马拉维", "馬拉威"), listOf("MWI")),
        row("MX", "墨西哥", "Mexico", 23.9200, -102.2894, listOf("mexico", "united mexican states", "墨西哥"), listOf("MEX")),
        row("MY", "马来西亚", "Malaysia", 3.1390, 101.6869, listOf("malaysia", "马来西亚", "馬來西亞", "马来西亚", "马来西亚", "吉隆坡", "malaysia", "kuala lumpur", "🇲🇾"), listOf("MYS")),
        row("MZ", "莫桑比克", "Mozambique", -13.9432, 37.8379, listOf("mozambique", "republic of mozambique", "莫桑比克", "莫三比克"), listOf("MOZ")),
        row("NA", "纳米比亚", "Namibia", -20.5753, 17.1082, listOf("namibia", "republic of namibia", "纳米比亚", "納米比亞"), listOf("NAM")),
        row("NC", "新喀里多尼亚", "New Caledonia", -21.0647, 165.0840, listOf("new caledonia", "新喀里多尼亚", "新喀里多尼亞"), listOf("NCL")),
        row("NE", "尼日尔", "Niger", 17.4462, 9.5044, listOf("niger", "republic of niger", "尼日尔", "尼日"), listOf("NER")),
        row("NG", "尼日利亚", "Nigeria", 9.4398, 7.5032, listOf("nigeria", "federal republic of nigeria", "尼日利亚", "奈及利亞"), listOf("NGA")),
        row("NI", "尼加拉瓜", "Nicaragua", 12.6707, -85.0693, listOf("nicaragua", "republic of nicaragua", "尼加拉瓜"), listOf("NIC")),
        row("NL", "荷兰", "Netherlands", 52.3676, 4.9041, listOf("netherlands", "kingdom of the netherlands", "荷兰", "荷蘭", "荷兰", "荷兰", "阿姆斯特丹", "netherlands", "holland", "amsterdam", "🇳🇱"), listOf("NLD")),
        row("NO", "挪威", "Norway", 61.3571, 9.6800, listOf("norway", "kingdom of norway", "挪威"), listOf("NOR")),
        row("NP", "尼泊尔", "Nepal", 28.2979, 83.6399, listOf("nepal", "尼泊尔", "尼泊爾"), listOf("NPL")),
        row("NZ", "新西兰", "New Zealand", -39.7590, 172.7870, listOf("new zealand", "新西兰", "新西蘭"), listOf("NZL")),
        row("OM", "阿曼", "Oman", 22.1204, 57.3366, listOf("oman", "sultanate of oman", "阿曼"), listOf("OMN")),
        row("PA", "巴拿马", "Panama", 8.7220, -80.3521, listOf("panama", "republic of panama", "巴拿马", "巴拿馬"), listOf("PAN")),
        row("PE", "秘鲁", "Peru", -12.9767, -72.9002, listOf("peru", "republic of peru", "秘鲁", "秘魯"), listOf("PER")),
        row("PG", "巴布亚新几内亚", "Papua New Guinea", -5.6953, 143.9102, listOf("papua new guinea", "independent state of papua new guinea", "巴布亚新几内亚", "巴布亞紐幾內亞"), listOf("PNG")),
        row("PH", "菲律宾", "Philippines", 14.5995, 120.9842, listOf("philippines", "republic of the philippines", "菲律宾", "菲律賓", "菲律宾", "菲律宾", "马尼拉", "philippines", "manila", "🇵🇭"), listOf("PHL")),
        row("PK", "巴基斯坦", "Pakistan", 29.3284, 68.5456, listOf("pakistan", "islamic republic of pakistan", "巴基斯坦"), listOf("PAK")),
        row("PL", "波兰", "Poland", 51.9903, 19.4905, listOf("poland", "republic of poland", "波兰", "波蘭"), listOf("POL")),
        row("PR", "波多黎各", "Puerto Rico", 18.2347, -66.4811, listOf("puerto rico", "commonwealth of puerto rico", "波多黎各"), listOf("PRI")),
        row("PS", "巴勒斯坦", "Palestine", 32.0474, 35.2913, listOf("palestine", "palestine (west bank and gaza)", "west bank and gaza", "巴勒斯坦", "巴勒斯坦地區"), listOf("PSE", "PSX")),
        row("PT", "葡萄牙", "Portugal", 39.6067, -8.2718, listOf("portugal", "portuguese republic", "葡萄牙"), listOf("PRT")),
        row("PY", "巴拉圭", "Paraguay", -21.6745, -60.1464, listOf("paraguay", "republic of paraguay", "巴拉圭"), listOf("PRY")),
        row("QA", "卡塔尔", "Qatar", 25.2374, 51.1435, listOf("qatar", "state of qatar", "卡塔尔", "卡達"), listOf("QAT")),
        row("RO", "罗马尼亚", "Romania", 45.7332, 24.9726, listOf("romania", "罗马尼亚", "羅馬尼亞"), listOf("ROU")),
        row("RS", "塞尔维亚", "Serbia", 44.1899, 20.7880, listOf("serbia", "republic of serbia", "塞尔维亚", "塞爾維亞"), listOf("SRB")),
        row("RU", "俄罗斯", "Russia", 55.7558, 37.6173, listOf("russia", "russian federation", "俄罗斯", "俄羅斯", "俄罗斯", "俄罗斯", "莫斯科", "russia", "moscow", "🇷🇺"), listOf("RUS")),
        row("RW", "卢旺达", "Rwanda", -1.8972, 30.1039, listOf("rwanda", "republic of rwanda", "卢旺达", "盧旺達"), listOf("RWA")),
        row("SA", "沙特阿拉伯", "Saudi Arabia", 23.8069, 44.6996, listOf("saudi arabia", "kingdom of saudi arabia", "沙特阿拉伯", "沙烏地阿拉伯"), listOf("SAU")),
        row("SB", "所罗门群岛", "Solomon Is.", -8.0295, 159.1705, listOf("solomon is.", "solomon islands", "所罗门群岛", "索羅門群島"), listOf("SLB")),
        row("SC", "塞舌尔", "Seychelles", -4.6796, 55.4920, listOf("seychelles"), listOf("SYC")),
        row("SD", "苏丹", "Sudan", 16.3307, 29.2607, listOf("sudan", "republic of the sudan", "苏丹", "蘇丹"), listOf("SDN")),
        row("SE", "瑞典", "Sweden", 65.8592, 19.0171, listOf("sweden", "kingdom of sweden", "瑞典"), listOf("SWE")),
        row("SG", "新加坡", "Singapore", 1.3521, 103.8198, listOf("singapore", "新加坡", "新加坡", "狮城", "singapore", "🇸🇬"), listOf("SGP")),
        row("SI", "斯洛文尼亚", "Slovenia", 46.0608, 14.9153, listOf("slovenia", "republic of slovenia", "斯洛文尼亚", "斯洛維尼亞"), listOf("SVN")),
        row("SK", "斯洛伐克", "Slovakia", 48.7340, 19.0499, listOf("slovakia", "slovak republic", "斯洛伐克"), listOf("SVK")),
        row("SL", "塞拉利昂", "Sierra Leone", 8.6174, -11.7637, listOf("sierra leone", "republic of sierra leone", "塞拉利昂", "獅子山"), listOf("SLE")),
        row("SM", "圣马力诺", "San Marino", 43.9424, 12.4578, listOf("san marino"), listOf("SMR")),
        row("SN", "塞内加尔", "Senegal", 15.1381, -14.7786, listOf("senegal", "republic of senegal", "塞内加尔", "塞內加爾"), listOf("SEN")),
        row("SO", "索马里", "Somalia", 3.5689, 45.1924, listOf("somalia", "federal republic of somalia", "索马里", "索馬利亞"), listOf("SOM")),
        row("SR", "苏里南", "Suriname", 4.1440, -55.9109, listOf("suriname", "republic of suriname", "苏里南", "蘇利南"), listOf("SUR")),
        row("SS", "南苏丹", "S. Sudan", 7.2305, 30.3902, listOf("s. sudan", "south sudan", "republic of south sudan", "南苏丹", "南蘇丹"), listOf("SSD", "SDS")),
        row("SV", "萨尔瓦多", "El Salvador", 13.6854, -88.8901, listOf("el salvador", "republic of el salvador", "萨尔瓦多", "薩爾瓦多"), listOf("SLV")),
        row("SY", "叙利亚", "Syria", 35.0066, 38.2778, listOf("syria", "syrian arab republic", "叙利亚", "敘利亞"), listOf("SYR")),
        row("SZ", "斯威士兰", "eSwatini", -26.5337, 31.4673, listOf("eswatini", "kingdom of eswatini", "swaziland", "斯威士兰", "史瓦帝尼"), listOf("SWZ")),
        row("TD", "乍得", "Chad", 15.1430, 18.6450, listOf("chad", "republic of chad", "乍得", "查德"), listOf("TCD")),
        row("TF", "法属南部和南极领地", "Fr. S. Antarctic Lands", -49.3037, 69.1221, listOf("fr. s. antarctic lands", "french southern and antarctic lands", "territory of the french southern and antarctic lands", "法属南部和南极领地", "法屬南部和南極領地"), listOf("ATF")),
        row("TG", "多哥", "Togo", 8.8072, 1.0581, listOf("togo", "togolese republic", "多哥"), listOf("TGO")),
        row("TH", "泰国", "Thailand", 13.7563, 100.5018, listOf("thailand", "kingdom of thailand", "泰国", "泰國", "泰国", "泰国", "曼谷", "thailand", "bangkok", "🇹🇭"), listOf("THA")),
        row("TJ", "塔吉克斯坦", "Tajikistan", 38.1998, 72.5873, listOf("tajikistan", "republic of tajikistan", "塔吉克斯坦", "塔吉克"), listOf("TJK")),
        row("TL", "东帝汶", "Timor-Leste", -8.8037, 125.8547, listOf("timor-leste", "east timor", "democratic republic of timor-leste", "东帝汶", "東帝汶"), listOf("TLS")),
        row("TM", "土库曼斯坦", "Turkmenistan", 39.8552, 58.6766, listOf("turkmenistan", "土库曼斯坦", "土庫曼"), listOf("TKM")),
        row("TN", "突尼斯", "Tunisia", 33.6873, 9.0079, listOf("tunisia", "republic of tunisia", "突尼斯", "突尼西亞"), listOf("TUN")),
        row("TR", "土耳其", "Turkey", 39.3454, 34.5083, listOf("turkey", "republic of turkey", "土耳其"), listOf("TUR")),
        row("TT", "特立尼达和多巴哥", "Trinidad and Tobago", 10.9989, -60.9184, listOf("trinidad and tobago", "republic of trinidad and tobago", "特立尼达和多巴哥", "千里達及托巴哥"), listOf("TTO")),
        row("TW", "台湾", "Taiwan", 25.0330, 121.5654, listOf("taiwan", "中华民国", "中華民國", "中华民国", "台湾", "台北", "taiwan", "taipei", "🇹🇼"), listOf("TWN")),
        row("TZ", "坦桑尼亚", "Tanzania", -6.0519, 34.9592, listOf("tanzania", "united republic of tanzania", "坦桑尼亚", "坦尚尼亞"), listOf("TZA")),
        row("UA", "乌克兰", "Ukraine", 49.7247, 32.1409, listOf("ukraine", "乌克兰", "烏克蘭"), listOf("UKR")),
        row("UG", "乌干达", "Uganda", 1.9726, 32.9486, listOf("uganda", "republic of uganda", "乌干达", "烏干達"), listOf("UGA")),
        row("US", "美国", "United States of America", 37.0902, -95.7129, listOf("united states of america", "united states", "美国", "美國", "美国", "美国", "洛杉矶", "硅谷", "西雅图", "纽约", "united states", "united states of america", "america", "los angeles", "san jose", "new york", "🇺🇸"), listOf("USA")),
        row("UY", "乌拉圭", "Uruguay", -32.9611, -55.9669, listOf("uruguay", "oriental republic of uruguay", "乌拉圭", "烏拉圭"), listOf("URY")),
        row("UZ", "乌兹别克斯坦", "Uzbekistan", 41.6936, 64.0054, listOf("uzbekistan", "republic of uzbekistan", "乌兹别克斯坦", "烏茲別克"), listOf("UZB")),
        row("VE", "委内瑞拉", "Venezuela", 7.1825, -64.5994, listOf("venezuela", "venezuela, rb", "bolivarian republic of venezuela", "委内瑞拉", "委內瑞拉"), listOf("VEN")),
        row("VN", "越南", "Vietnam", 10.8231, 106.6297, listOf("vietnam", "socialist republic of vietnam", "越南", "越南", "越南", "胡志明", "河内", "vietnam", "ho chi minh", "hanoi", "🇻🇳"), listOf("VNM")),
        row("VU", "瓦努阿图", "Vanuatu", -15.3715, 166.9088, listOf("vanuatu", "republic of vanuatu", "瓦努阿图", "萬那杜"), listOf("VUT")),
        row("XK", "科索沃", "Kosovo", 42.5936, 20.8607, listOf("kosovo", "republic of kosovo", "科索沃"), listOf("KOS")),
        row("YE", "也门", "Yemen", 15.3282, 45.8744, listOf("yemen", "yemen, rep.", "republic of yemen", "也门", "葉門"), listOf("YEM")),
        row("ZA", "南非", "South Africa", -29.7088, 23.6657, listOf("south africa", "republic of south africa", "南非"), listOf("ZAF")),
        row("ZM", "赞比亚", "Zambia", -14.6608, 26.3953, listOf("zambia", "republic of zambia", "赞比亚", "尚比亞"), listOf("ZMB")),
        row("ZW", "津巴布韦", "Zimbabwe", -18.9116, 29.9254, listOf("zimbabwe", "republic of zimbabwe", "津巴布韦", "辛巴威"), listOf("ZWE")),
    )

    private val curatedCodes = mapOf(
        "HK" to listOf("hk", "hkg"),
        "JP" to listOf("jp", "jpn", "nrt", "hnd", "kix"),
        "SG" to listOf("sg", "sgp", "sin"),
        "TW" to listOf("tw", "twn", "tpe"),
        "KR" to listOf("kr", "kor", "sel", "icn"),
        "US" to listOf("us", "usa", "lax", "sfo", "sjc", "sea", "nyc"),
        "CA" to listOf("ca", "can", "yyz", "yvr"),
        "GB" to listOf("uk", "gbr", "lon", "lhr"),
        "DE" to listOf("de", "deu", "fra", "ber"),
        "FR" to listOf("fr", "par", "cdg"),
        "NL" to listOf("nl", "nld", "ams"),
        "AU" to listOf("au", "aus", "syd", "mel"),
        "MY" to listOf("my", "mys", "kul"),
        "TH" to listOf("th", "tha", "bkk"),
        "VN" to listOf("vn", "vnm", "sgn", "han"),
        "PH" to listOf("ph", "phl", "mnl"),
        "IN" to listOf("in", "ind", "bom", "blr"),
        "AE" to listOf("ae", "are", "dxb"),
        "RU" to listOf("ru", "rus", "mow", "svo"),
        "BR" to listOf("br", "bra", "gru"),
    )

    private val cities = mapOf(
        "johannesburg" to "ZA", "约翰内斯堡" to "ZA", "cape town" to "ZA", "开普敦" to "ZA",
        "istanbul" to "TR", "伊斯坦布尔" to "TR", "伊斯坦堡" to "TR", "ankara" to "TR",
        "warsaw" to "PL", "华沙" to "PL", "prague" to "CZ", "布拉格" to "CZ",
        "vienna" to "AT", "维也纳" to "AT", "zurich" to "CH", "苏黎世" to "CH",
        "geneva" to "CH", "日内瓦" to "CH", "brussels" to "BE", "布鲁塞尔" to "BE",
        "madrid" to "ES", "马德里" to "ES", "barcelona" to "ES", "巴塞罗那" to "ES",
        "milan" to "IT", "米兰" to "IT", "rome" to "IT", "罗马" to "IT",
        "lisbon" to "PT", "里斯本" to "PT", "athens" to "GR", "雅典" to "GR",
        "stockholm" to "SE", "斯德哥尔摩" to "SE", "oslo" to "NO", "奥斯陆" to "NO",
        "helsinki" to "FI", "赫尔辛基" to "FI", "copenhagen" to "DK", "哥本哈根" to "DK",
        "dublin" to "IE", "都柏林" to "IE", "reykjavik" to "IS", "雷克雅未克" to "IS",
        "bucharest" to "RO", "布加勒斯特" to "RO", "sofia" to "BG", "索菲亚" to "BG",
        "budapest" to "HU", "布达佩斯" to "HU", "kyiv" to "UA", "kiev" to "UA",
        "基辅" to "UA", "buenos aires" to "AR", "布宜诺斯艾利斯" to "AR", "santiago" to "CL",
        "圣地亚哥" to "CL", "lima" to "PE", "利马" to "PE", "bogota" to "CO",
        "波哥大" to "CO", "mexico city" to "MX", "墨西哥城" to "MX", "cairo" to "EG",
        "开罗" to "EG", "lagos" to "NG", "拉各斯" to "NG", "nairobi" to "KE",
        "内罗毕" to "KE", "casablanca" to "MA", "卡萨布兰卡" to "MA", "tel aviv" to "IL",
        "特拉维夫" to "IL", "riyadh" to "SA", "利雅得" to "SA", "doha" to "QA",
        "多哈" to "QA", "karachi" to "PK", "卡拉奇" to "PK", "dhaka" to "BD",
        "达卡" to "BD", "colombo" to "LK", "科伦坡" to "LK", "kathmandu" to "NP",
        "加德满都" to "NP", "almaty" to "KZ", "阿拉木图" to "KZ", "tashkent" to "UZ",
        "塔什干" to "UZ", "auckland" to "NZ", "奥克兰" to "NZ", "wellington" to "NZ",
        "惠灵顿" to "NZ", "jakarta" to "ID", "雅加达" to "ID", "phnom penh" to "KH",
        "金边" to "KH", "yangon" to "MM", "仰光" to "MM", "vientiane" to "LA",
        "万象" to "LA", "ulaanbaatar" to "MN", "乌兰巴托" to "MN",
    )

    val countries: List<CountryEntry> = rows.map { it.entry }
    private val byCode = countries.associateBy { it.code }
    operator fun get(code: String): CountryEntry? {
        val normalized = code.trim().uppercase(Locale.ROOT)
        return byCode[if (normalized == "UK") "GB" else normalized]
    }

    private val flagPattern = Regex("[\\x{1F1E6}-\\x{1F1FF}]{2}")
    private val words = Regex("[\\p{L}]+")
    private val reserved = setOf("SS", "SSR", "WS", "TLS", "TCP", "UDP", "QUIC", "KCP", "GRPC",
        "IPLC", "IEPL", "BGP", "CDN", "NAT", "VPN", "API", "DNS", "MUX", "KB", "MB", "GB", "TB")
    private data class Phrase(val code: String, val text: String, val han: Boolean)
    private fun normalize(value: String) = words.findAll(value.lowercase(Locale.ROOT))
        .joinToString(" ") { it.value }

    private val phrases = buildList {
        fun addPhrase(code: String, text: String) {
            val normalized = normalize(text)
            if (normalized.isNotEmpty()) add(Phrase(code, normalized,
                normalized.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }))
        }
        cities.forEach { (name, code) -> addPhrase(code, name) }
        rows.forEach { row ->
            (row.aliases + row.entry.chineseName + row.entry.englishName).forEach { addPhrase(row.entry.code, it) }
        }
        addPhrase("CN", "中国")
        addPhrase("CN", "中國")
    }.distinct().sortedByDescending { it.text.length }

    private val codes = buildMap {
        rows.forEach { row ->
            (row.codes + row.entry.code).forEach { if (it !in reserved) put(it, row.entry.code) }
        }
        curatedCodes.forEach { (code, aliases) ->
            aliases.forEach { if (it.uppercase(Locale.ROOT) !in reserved) put(it.uppercase(Locale.ROOT), code) }
        }
    }

    internal fun codeFromNodeName(node: ProxyNode): String? {
        if (node.name.equals(node.server, true) || node.name.equals(endpoint(node), true)) return null
        return codeFromName(node.name)
    }

    fun codeFromName(name: String): String? {
        flagPattern.findAll(name).forEach { flag ->
            val code = flag.value.codePoints().toArray().map { ('A'.code + it - 0x1F1E6).toChar() }.joinToString("")
            if (code in byCode) return code
        }
        val normalized = " " + normalize(name) + " "
        phrases.firstOrNull { phrase ->
            if (phrase.han) normalized.contains(phrase.text) else normalized.contains(" ${phrase.text} ")
        }?.let { return it.code }
        return words.findAll(name).mapNotNull { codes[it.value] }.firstOrNull()
    }
}

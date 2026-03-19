package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * TopicExtractor — extracts canonical topic → strength mappings from a SocialPost.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  REGIONAL LANGUAGE SUPPORT (v3)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  Each of the 10 major Indian script languages has its own native-script
 *  keyword map covering the ~15 highest-frequency civic + entertainment topics.
 *  All maps are merged into ALL_LANG_KEYWORD_MAP at class-load time (immutable).
 *
 *  Lookup order for each token:
 *    1. ALL_LANG_KEYWORD_MAP (native script, exact match, O(1))
 *    2. ROMAN_KEYWORD_MAP    (English/transliterated, O(1))
 *    3. Prefix scan on ROMAN_KEYWORD_MAP for compound words
 *
 *  Step 1 MUST happen on the raw (un-normalised) token because normalise()
 *  strips non-ASCII characters, destroying Devanagari/Tamil etc.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  SIGNAL STRENGTHS
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   Hashtag       1.00  — explicit declaration, highest confidence
 *   Category      0.90  — community category tag
 *   Community tag 0.75  — community interest tags
 *   Poll text     0.70  — user wrote about this topic
 *   Body content  0.60  — mentioned in the post body
 */
@Component
public class TopicExtractor {

    private static final double HASHTAG_STRENGTH  = 1.00;
    private static final double CATEGORY_STRENGTH = 0.90;
    private static final double COMM_TAG_STRENGTH = 0.75;
    private static final double POLL_STRENGTH     = 0.70;
    private static final double CONTENT_STRENGTH  = 0.60;

    // ═══════════════════════════════════════════════════════════════════════════
    //  ROMAN / TRANSLITERATED KEYWORD MAP
    // ═══════════════════════════════════════════════════════════════════════════

    private static final Map<String, String> ROMAN_KEYWORD_MAP;
    static {
        Map<String, String> m = new HashMap<>(800);

        put(m, "roads",          "roads","road","pothole","potholes","traffic","highway","flyover",
                "underpass","signal","zebra","divider","sidewalk","footpath","roadwork","nala",
                "drain","drainage","gutter","manhole","sewage","sewer");
        put(m, "water_supply",   "water","pani","paani","pipeline","leakage","tap","borewell",
                "groundwater","tanker","waterboard","jal","jalboard","nalajal",
                "neer","jalam","vanam");                                    // Tamil/Telugu transliterations
        put(m, "electricity",    "electricity","bijli","power","powercut","loadshedding","transformer",
                "meter","msedcl","bescom","tneb","streetlight","light","wiring","short_circuit",
                "current","vij","vidyut");
        put(m, "sanitation",     "garbage","kachra","dustbin","municipal","cleaning","sweeper",
                "solid_waste","landfill","compost","segregation","wet_waste","dry_waste","bhangaar",
                "safai","swachhata");
        put(m, "parks",          "park","garden","playground","tree","green","plant","nursery",
                "jogging","walking","recreation");
        put(m, "housing",        "housing","flat","apartment","building","construction","demolition",
                "slum","jhuggi","eviction","property","real_estate","plot","rent","tenant","landlord");
        put(m, "public_transport","bus","auto","rickshaw","metro","local","train","railway",
                "ola","uber","rapido","cab","taxi","commute","station","depot");
        put(m, "local_governance","corporator","ward","municipality","nagar_palika","pmc","bmc",
                "mcgm","bbmp","complaint","grievance","petition","mayor","council","mla","mp",
                "sarpanch","panchayat","gram_sabha","rti","government","sarkar");
        put(m, "corruption",     "corruption","bhrashtachar","bribery","scam","fraud","encroachment",
                "illegal","nexus","kickback","tenderscam");
        put(m, "politics",       "politics","election","vote","party","bjp","congress","aap","shiv_sena",
                "ncp","dmk","tmc","sp","bsp","rjd","manifesto","rally","campaign","candidate",
                "ballot","polling","booth","result","seat","constituency","vidhansabha","loksabha");
        put(m, "national_news",  "modi","parliament","budget","policy","scheme","yojana",
                "law","court","supreme_court","high_court","verdict","judgement","constitutional");
        put(m, "cricket",        "cricket","ipl","t20","odi","test","virat","rohit","dhoni","sachin",
                "bumrah","shami","jadeja","hardik","rishabh","sanju","sixer","wicket","century",
                "boundary","runout","drs","umpire","stadium","pitch","over","inning","bcci");
        put(m, "ipl",            "rcb","mi","csk","kkr","srh","rr","dc","gt","lsg","pbks",
                "mumbai_indians","chennai","kolkata","hyderabad","bangalore","franchise","auction",
                "retention","mega_auction","points_table","qualifier","eliminator","final");
        put(m, "football",       "football","soccer","fifa","isl","premier_league","ronaldo","messi",
                "mbappe","mohunbagan","eastbengal","goa_fc","goal","freekick","penalty","offside");
        put(m, "kabaddi",        "kabaddi","pkl","pro_kabaddi","raider","tackle","super_raid");
        put(m, "badminton",      "badminton","pv_sindhu","saina","shuttlecock","smash","bwf");
        put(m, "fitness",        "fitness","gym","workout","yoga","zumba","crossfit","marathon",
                "running","cycling","swimming","diet","weight","protein","cardio");
        put(m, "bollywood",      "bollywood","film","movie","cinema","trailer","release","actor",
                "actress","director","srk","shahrukh","salman","aamir","ranveer","deepika",
                "alia","kareena","priyanka","anushka","katrina","hrithik","ranbir","jawan",
                "pathaan","animal","dunki","kgf","bahubali","ott","netflix","amazon","hotstar",
                "disney","zee5","sonyliv");
        put(m, "music",          "music","song","gana","album","singer","rapper","rap","hip_hop",
                "pop","classic","devotional","arijit","atif","diljit","badshah","honey_singh",
                "ar_rahman","spotify","gaana","jiosaavn","youtube_music");
        put(m, "webseries",      "webseries","series","season","episode","binge","mirzapur",
                "scam1992","panchayat","delhi_crime","brahmastra","tvf","money_heist");
        put(m, "comedy",         "comedy","standup","roast","meme","reel","funny","viral","joke",
                "troll","parody","satire");
        put(m, "food",           "food","khana","restaurant","hotel","dhaba","cafe","street_food",
                "biryani","pizza","burger","dosa","idli","samosa","chaat","pav_bhaji","vada_pav",
                "thali","recipe","cooking","baking","chef","zomato","swiggy","food_delivery",
                "sadam","saapadu","oota");                                 // Tamil/Kannada
        put(m, "technology",     "tech","technology","startup","ai","ml","machine_learning","chatgpt",
                "openai","gemini","llm","coding","programming","developer","software","app",
                "mobile","android","ios","laptop","computer","gadget","review","unboxing");
        put(m, "gaming",         "gaming","game","bgmi","freefire","pubg","valorant","minecraft",
                "gta","cod","esports","ps5","xbox","nintendo","steam");
        put(m, "finance",        "finance","money","stock","market","sensex","nifty","mutual_fund",
                "sip","investment","portfolio","shares","equity","ipo","sebi","rbi","loan","emi",
                "bank","gold","silver","inflation","gdp","economy","budget");
        put(m, "jobs",           "jobs","job","career","hiring","recruitment","interview","resume",
                "internship","fresher","salary","appraisal","layoff","wfh","remote",
                "naukri","nokri","velai","udyog");
        put(m, "education",      "education","school","college","university","exam","result","neet",
                "jee","upsc","mpsc","gate","cat","clat","board","cbse","ssc","railway","bank_exam",
                "coaching","study","tuition","scholarship","iit","nit","iim",
                "vidyalaya","shiksha","padhai","kalvi","vidya");
        put(m, "health",         "health","hospital","doctor","medicine","disease","covid","dengue",
                "malaria","fever","vaccination","ayurveda","homeopathy","mental_health","therapy",
                "depression","anxiety","yoga_health","diet","nutrition","blood_donation","icu",
                "arogya","swasthya","unavu","maruntu");
        put(m, "environment",    "pollution","air_quality","aqi","smog","flood","drought","climate",
                "global_warming","carbon","forest","wildlife","tiger","leopard","conservation",
                "clean_energy","solar","wind_energy","ev","electric_vehicle",
                "prakriti","paryavaran","suttru_suzhal");
        put(m, "weather",        "rain","monsoon","flood","heatwave","cold","winter","fog","cyclone",
                "earthquake","tsunami","disaster","relief","ndrf",
                "mazhai","varsham","mausam","pausa");
        put(m, "religion",       "temple","mandir","masjid","church","gurudwara","prayer","puja",
                "namaz","mass","holy","divine","god","bhagwan","allah","jesus","guru",
                "kovil","devalaya","palliyile");
        put(m, "festivals",      "festival","diwali","holi","eid","christmas","navratri","ganesh",
                "durga","onam","pongal","baisakhi","ugadi","dussehra","raksha","janmashtami",
                "independence_day","republic_day","bharat",
                "vishu","bihu","lohri","makar_sankranti","gudi_padwa");
        put(m, "culture",        "culture","tradition","heritage","art","handicraft","folk","classical",
                "dance","bharatnatyam","kathak","odissi","classical_music","folk_music",
                "kuchipudi","mohiniyattam","manipuri","yakshagana");
        put(m, "women",          "women","mahila","girl","feminist","equality","safety","harassment",
                "metoo","domestic_violence","empowerment","self_help","ngo",
                "pen","pengal","stree");
        put(m, "social",         "social","community","ngo","volunteer","donation","charity",
                "helpline","welfare","senior_citizen","disability","inclusion");
        put(m, "travel",         "travel","trip","tour","tourism","holiday","vacation","trek",
                "backpack","hotel_stay","resort","beach","mountain","temple_tour","heritage_walk",
                "makemytrip","goibibo","irctc","airport","flight",
                "yatra","paryatan","payanam");
        put(m, "local_events",   "event","concert","exhibition","workshop","seminar","meetup",
                "fair","mela","yatra","procession","march","protest");
        put(m, "pets",           "dog","cat","pet","puppy","kitten","animal","stray","shelter",
                "adoption","rescue","paws","feeder","animal_rights","spca");

        ROMAN_KEYWORD_MAP = Collections.unmodifiableMap(m);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  NATIVE-SCRIPT KEYWORD MAPS  (one per language)
    // ═══════════════════════════════════════════════════════════════════════════
    //
    //  Coverage: 15 highest-frequency civic + entertainment topics per language.
    //  Keys: exact Unicode strings as users type them in hashtags / body text.
    //  Values: canonical topic name matching ROMAN_KEYWORD_MAP.
    //
    //  Approach: cover each category's most-used single-word keyword in that
    //  language. Long-tail content falls through to CONTENT_STRENGTH (0.60)
    //  via body-text scan, which is acceptable.

    // ── Hindi (Devanagari) ─────────────────────────────────────────────────────
    private static final Map<String, String> HINDI_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(30);
        m.put("\u0938\u095C\u0915", "roads");           // सड़क
        m.put("\u0938\u095C\u0915\u0947\u0902", "roads"); // सड़कें
        m.put("\u092A\u093E\u0928\u0940", "water_supply"); // पानी
        m.put("\u091C\u0932", "water_supply");           // जल
        m.put("\u092C\u093F\u091C\u0932\u0940", "electricity"); // बिजली
        m.put("\u0938\u092B\u093E\u0908", "sanitation"); // सफाई
        m.put("\u0915\u091A\u0930\u093E", "sanitation"); // कचरा
        m.put("\u0938\u094D\u0935\u093E\u0938\u094D\u0925\u094D\u092F", "health"); // स्वास्थ्य
        m.put("\u0905\u0938\u094D\u092A\u0924\u093E\u0932", "health"); // अस्पताल
        m.put("\u0936\u093F\u0915\u094D\u0937\u093E", "education"); // शिक्षा
        m.put("\u0938\u094D\u0915\u0942\u0932", "education"); // स्कूल
        m.put("\u0915\u094D\u0930\u093F\u0915\u0947\u091F", "cricket"); // क्रिकेट
        m.put("\u0930\u093E\u091C\u0928\u0940\u0924\u093F", "politics"); // राजनीति
        m.put("\u091A\u0941\u0928\u093E\u0935", "politics"); // चुनाव
        m.put("\u0924\u094D\u092F\u094B\u0939\u093E\u0930", "festivals"); // त्योहार
        m.put("\u0916\u093E\u0928\u093E", "food");       // खाना
        m.put("\u0930\u094B\u091C\u0917\u093E\u0930", "jobs"); // रोजगार
        m.put("\u0928\u094C\u0915\u0930\u0940", "jobs"); // नौकरी
        m.put("\u092E\u094C\u0938\u092E", "weather");    // मौसम
        m.put("\u092C\u0930\u0938\u093E\u0924", "weather"); // बरसात
        m.put("\u092A\u094D\u0930\u0926\u0942\u0937\u0923", "environment"); // प्रदूषण
        m.put("\u0938\u0902\u0917\u0940\u0924", "music"); // संगीत
        m.put("\u092B\u093F\u0932\u094D\u092E", "bollywood"); // फिल्म
        m.put("\u0916\u0947\u0932", "fitness");          // खेल
        m.put("\u092A\u0930\u093F\u0935\u0939\u0928", "public_transport"); // परिवहन
        HINDI_MAP = Collections.unmodifiableMap(m);
    }

    // ── Marathi (Devanagari — distinct vocabulary) ────────────────────────────
    private static final Map<String, String> MARATHI_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(25);
        m.put("\u0930\u0938\u094D\u0924\u093E", "roads");   // रस्ता
        m.put("\u0930\u0938\u094D\u0924\u0947", "roads");   // रस्ते
        m.put("\u092A\u093E\u0923\u0940", "water_supply");  // पाणी
        m.put("\u0935\u0940\u091C", "electricity");         // वीज
        m.put("\u0938\u094D\u0935\u091A\u094D\u091B\u0924\u093E", "sanitation"); // स्वच्छता
        m.put("\u0906\u0930\u094B\u0917\u094D\u092F", "health"); // आरोग्य
        m.put("\u0930\u0941\u0917\u094D\u0923\u093E\u0932\u092F", "health"); // रुग्णालय
        m.put("\u0936\u093F\u0915\u094D\u0937\u0923", "education"); // शिक्षण
        m.put("\u0915\u094D\u0930\u093F\u0915\u0947\u091F", "cricket"); // क्रिकेट
        m.put("\u0930\u093E\u091C\u0915\u093E\u0930\u0923", "politics"); // राजकारण
        m.put("\u0928\u093F\u0935\u0921\u0923\u0942\u0915", "politics"); // निवडणूक
        m.put("\u0938\u0923", "festivals");                 // सण
        m.put("\u091C\u0947\u0935\u0923", "food");         // जेवण
        m.put("\u0928\u094B\u0915\u0930\u0940", "jobs");   // नोकरी
        m.put("\u092A\u093E\u0909\u0938", "weather");      // पाऊस
        m.put("\u092A\u094D\u0930\u0926\u0942\u0937\u0923", "environment"); // प्रदूषण
        m.put("\u0938\u0902\u0917\u0940\u0924", "music");  // संगीत
        m.put("\u091A\u093F\u0924\u094D\u0930\u092A\u091F", "bollywood"); // चित्रपट
        MARATHI_MAP = Collections.unmodifiableMap(m);
    }

    // ── Bengali (বাংলা) ───────────────────────────────────────────────────────
    private static final Map<String, String> BENGALI_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(25);
        m.put("\u09B0\u09BE\u09B8\u09CD\u09A4\u09BE", "roads");         // রাস্তা
        m.put("\u09AA\u09BE\u09A8\u09BF", "water_supply");              // পানি
        m.put("\u099C\u09B2", "water_supply");                          // জল
        m.put("\u09AC\u09BF\u09A6\u09CD\u09AF\u09C1\u09CE", "electricity"); // বিদ্যুৎ
        m.put("\u09AA\u09B0\u09BF\u09B7\u09CD\u0995\u09BE\u09B0", "sanitation"); // পরিষ্কার
        m.put("\u09B8\u09CD\u09AC\u09BE\u09B8\u09CD\u09A5\u09CD\u09AF", "health"); // স্বাস্থ্য
        m.put("\u09B9\u09BE\u09B8\u09AA\u09BE\u09A4\u09BE\u09B2", "health"); // হাসপাতাল
        m.put("\u09B6\u09BF\u0995\u09CD\u09B7\u09BE", "education");    // শিক্ষা
        m.put("\u09B8\u09CD\u0995\u09C1\u09B2", "education");          // স্কুল
        m.put("\u0995\u09CD\u09B0\u09BF\u0995\u09C7\u099F", "cricket"); // ক্রিকেট
        m.put("\u09B0\u09BE\u099C\u09A8\u09C0\u09A4\u09BF", "politics"); // রাজনীতি
        m.put("\u09A8\u09BF\u09B0\u09CD\u09AC\u09BE\u099A\u09A8", "politics"); // নির্বাচন
        m.put("\u0989\u09CE\u09B8\u09AC", "festivals");                 // উৎসব
        m.put("\u0996\u09BE\u09AC\u09BE\u09B0", "food");               // খাবার
        m.put("\u099A\u09BE\u0995\u09B0\u09BF", "jobs");               // চাকরি
        m.put("\u09AC\u09C3\u09B7\u09CD\u099F\u09BF", "weather");      // বৃষ্টি
        m.put("\u09AC\u09A8\u09CD\u09AF\u09BE", "weather");            // বন্যা
        m.put("\u09B8\u0999\u09CD\u0997\u09C0\u09A4", "music");        // সঙ্গীত
        m.put("\u099B\u09AC\u09BF", "bollywood");                       // ছবি (film)
        m.put("\u09AA\u09B0\u09BF\u09AC\u09B9\u09A8", "public_transport"); // পরিবহন
        BENGALI_MAP = Collections.unmodifiableMap(m);
    }

    // ── Telugu (తెలుగు) ───────────────────────────────────────────────────────
    private static final Map<String, String> TELUGU_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(25);
        m.put("\u0C30\u0C4B\u0C21\u0C4D\u0C21\u0C41", "roads");        // రోడ్డు
        m.put("\u0C28\u0C40\u0C33\u0C4D\u0C33\u0C41", "water_supply"); // నీళ్ళు
        m.put("\u0C35\u0C3F\u0C26\u0C4D\u0C2F\u0C41\u0C24\u0C4D", "electricity"); // విద్యుత్
        m.put("\u0C2A\u0C30\u0C3F\u0C36\u0C41\u0C2D\u0C4D\u0C30\u0C24", "sanitation"); // పరిశుభ్రత
        m.put("\u0C06\u0C30\u0C4B\u0C17\u0C4D\u0C2F\u0C02", "health"); // ఆరోగ్యం
        m.put("\u0C06\u0C38\u0C41\u0C2A\u0C24\u0C4D\u0C30\u0C3F", "health"); // ఆసుపత్రి
        m.put("\u0C35\u0C3F\u0C26\u0C4D\u0C2F", "education");          // విద్య
        m.put("\u0C2C\u0C21\u0C3F", "education");                       // బడి (school)
        m.put("\u0C15\u0C4D\u0C30\u0C3F\u0C15\u0C46\u0C1F\u0C4D", "cricket"); // క్రికెట్
        m.put("\u0C30\u0C3E\u0C1C\u0C15\u0C40\u0C2F\u0C3E\u0C32\u0C41", "politics"); // రాజకీయాలు
        m.put("\u0C0E\u0C28\u0C4D\u0C28\u0C3F\u0C15", "politics");    // ఎన్నిక
        m.put("\u0C2A\u0C02\u0C21\u0C17", "festivals");                 // పండగ
        m.put("\u0C24\u0C3F\u0C02\u0C21\u0C3F", "food");               // తిండి
        m.put("\u0C09\u0C26\u0C4D\u0C2F\u0C4B\u0C17\u0C02", "jobs");  // ఉద్యోగం
        m.put("\u0C35\u0C3E\u0C24\u0C3E\u0C35\u0C30\u0C23\u0C02", "weather"); // వాతావరణం
        m.put("\u0C35\u0C30\u0C4D\u0C37\u0C02", "weather");            // వర్షం
        m.put("\u0C38\u0C02\u0C17\u0C40\u0C24\u0C02", "music");        // సంగీతం
        m.put("\u0C38\u0C3F\u0C28\u0C3F\u0C2E\u0C3E", "bollywood");   // సినిమా
        TELUGU_MAP = Collections.unmodifiableMap(m);
    }

    // ── Tamil (தமிழ்) ─────────────────────────────────────────────────────────
    private static final Map<String, String> TAMIL_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(25);
        m.put("\u0B9A\u0BBE\u0BB2\u0BC8", "roads");                     // சாலை
        m.put("\u0BA4\u0BA3\u0BCD\u0BA3\u0BC0\u0BB0\u0BCD", "water_supply"); // தண்ணீர்
        m.put("\u0BAE\u0BBF\u0BA9\u0BCD\u0B9A\u0BBE\u0BB0\u0BAE\u0BCD", "electricity"); // மின்சாரம்
        m.put("\u0B9A\u0BC1\u0BA4\u0BCD\u0BA4\u0BAE\u0BCD", "sanitation"); // சுத்தம்
        m.put("\u0B89\u0B9F\u0BB2\u0BCD\u0BA8\u0BB2\u0BAE\u0BCD", "health"); // உடல்நலம்
        m.put("\u0BAE\u0BB0\u0BC1\u0BA4\u0BCD\u0BA4\u0BC1\u0BB5\u0BAE\u0BA9\u0BC8", "health"); // மருத்துவமனை
        m.put("\u0B95\u0BB2\u0BCD\u0BB5\u0BBF", "education");           // கல்வி
        m.put("\u0BAA\u0BB3\u0BCD\u0BB3\u0BBF", "education");           // பள்ளி
        m.put("\u0B95\u0BBF\u0BB0\u0BBF\u0B95\u0BCD\u0B95\u0BC6\u0B9F\u0BCD", "cricket"); // கிரிக்கெட்
        m.put("\u0A85\u0BB0\u0B9A\u0BBF\u0BAF\u0BB2\u0BCD", "politics"); // அரசியல்
        m.put("\u0BA4\u0BC7\u0BB0\u0BCD\u0BA4\u0BB2\u0BCD", "politics"); // தேர்தல்
        m.put("\u0BA4\u0BBF\u0BB0\u0BC1\u0BB5\u0BBF\u0BB4\u0BBE", "festivals"); // திருவிழா
        m.put("\u0BB5\u0BBF\u0BB4\u0BBE", "festivals");                 // விழா
        m.put("\u0E2D\u0BA3\u0BB5\u0BC1", "food");                      // உணவு
        m.put("\u0BB5\u0BC7\u0BB2\u0BC8", "jobs");                      // வேலை
        m.put("\u0BAE\u0BB4\u0BC8", "weather");                         // மழை
        m.put("\u0BB5\u0BBE\u0BA9\u0BBF\u0BB2\u0BC8", "weather");      // வானிலை
        m.put("\u0B87\u0B9A\u0BC8", "music");                           // இசை
        m.put("\u0BAA\u0B9F\u0BAE\u0BCD", "bollywood");                 // படம் (film)
        m.put("\u0BAA\u0BA3\u0BAE\u0BCD", "finance");                   // பணம்
        TAMIL_MAP = Collections.unmodifiableMap(m);
    }

    // ── Gujarati (ગુજરાતી) ────────────────────────────────────────────────────
    private static final Map<String, String> GUJARATI_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(22);
        m.put("\u0AB0\u0AB8\u0ACD\u0AA4\u0ACB", "roads");              // રસ્તો
        m.put("\u0AAA\u0ABE\u0AA3\u0AC0", "water_supply");             // પાણી
        m.put("\u0AB5\u0AC0\u0A9C\u0AB3\u0AC0", "electricity");        // વીજળી
        m.put("\u0AB8\u0AAB\u0ABE\u0A88", "sanitation");               // સફાઈ
        m.put("\u0A86\u0AB0\u0ACB\u0A97\u0ACD\u0AAF", "health");      // આરોગ્ય
        m.put("\u0AB9\u0AC7\u0AB2\u0ACD\u0AA5", "health");             // હેલ્થ
        m.put("\u0AB6\u0ABF\u0A95\u0ACD\u0AB7\u0AA3", "education");   // શિક્ષણ
        m.put("\u0A95\u0ACD\u0AB0\u0ABF\u0A95\u0AC7\u0A9F", "cricket"); // ક્રિકેટ
        m.put("\u0AB0\u0ABE\u0A9C\u0A95\u0ABE\u0AB0\u0AA3", "politics"); // રાજકારણ
        m.put("\u0A9A\u0AC1\u0A82\u0A9F\u0AA3\u0AC0", "politics");    // ચૂંટણી
        m.put("\u0AA4\u0AB9\u0AC7\u0AB5\u0ABE\u0AB0", "festivals");   // તહેવાર
        m.put("\u0A96\u0ABE\u0AA3\u0AC1\u0A82", "food");              // ખાણું
        m.put("\u0AA8\u0ACB\u0A95\u0AB0\u0AC0", "jobs");              // નોકરી
        m.put("\u0AB9\u0AB5\u0ABE\u0AAE\u0ABE\u0AA8", "weather");     // હવામાન
        m.put("\u0AB5\u0AB0\u0AB8\u0ABE\u0AA6", "weather");           // વરસાદ
        m.put("\u0AB8\u0A82\u0A97\u0AC0\u0AA4", "music");             // સંગીત
        m.put("\u0AAB\u0ABF\u0AB2\u0ACD\u0AAE", "bollywood");         // ફિલ્મ
        GUJARATI_MAP = Collections.unmodifiableMap(m);
    }

    // ── Kannada (ಕನ್ನಡ) ──────────────────────────────────────────────────────
    private static final Map<String, String> KANNADA_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(22);
        m.put("\u0CB0\u0CB8\u0CCD\u0CA4\u0CC6", "roads");              // ರಸ್ತೆ
        m.put("\u0CA8\u0CC0\u0CB0\u0CC1", "water_supply");             // ನೀರು
        m.put("\u0CB5\u0CBF\u0CA6\u0CCD\u0CAF\u0CC1\u0CA4\u0CCD", "electricity"); // ವಿದ್ಯುತ್
        m.put("\u0CB8\u0CCD\u0CB5\u0C9A\u0CCD\u0C9B\u0CA4\u0CC6", "sanitation"); // ಸ್ವಚ್ಛತೆ
        m.put("\u0C86\u0CB0\u0CCB\u0C97\u0CCD\u0CAF", "health");       // ಆರೋಗ್ಯ
        m.put("\u0C86\u0CB8\u0CCD\u0CAA\u0CA4\u0CCD\u0CB0\u0CC6", "health"); // ಆಸ್ಪತ್ರೆ
        m.put("\u0CB6\u0CBF\u0C95\u0CCD\u0CB7\u0CA3", "education");    // ಶಿಕ್ಷಣ
        m.put("\u0C95\u0CCD\u0CB0\u0CBF\u0C95\u0CC6\u0C9F\u0CCD", "cricket"); // ಕ್ರಿಕೆಟ್
        m.put("\u0CB0\u0CBE\u0C9C\u0C95\u0CC0\u0CAF", "politics");    // ರಾಜಕೀಯ
        m.put("\u0C9A\u0CC1\u0CA8\u0CBE\u0CB5\u0CA3\u0CC6", "politics"); // ಚುನಾವಣೆ
        m.put("\u0CB9\u0CAC\u0CCD\u0CAC", "festivals");                // ಹಬ್ಬ
        m.put("\u0CA4\u0CBF\u0C82\u0CA1\u0CBF", "food");              // ತಿಂಡಿ
        m.put("\u0CA4\u0CBF\u0CA8\u0CCD\u0CA8\u0CC1", "food");        // ತಿನ್ನು
        m.put("\u0C89\u0CA6\u0CCD\u0CAF\u0CCB\u0C97", "jobs");        // ಉದ್ಯೋಗ
        m.put("\u0CB9\u0CB5\u0CBE\u0CAE\u0CBE\u0CA8", "weather");     // ಹವಾಮಾನ
        m.put("\u0BAE\u0BB4\u0BC8", "weather");                        // மழை (borrowed Tamil word common in Kannada speech)
        m.put("\u0CB8\u0C82\u0C97\u0CC0\u0CA4", "music");             // ಸಂಗೀತ
        m.put("\u0C9A\u0CBF\u0CA4\u0CCD\u0CB0", "bollywood");         // ಚಿತ್ರ (film)
        KANNADA_MAP = Collections.unmodifiableMap(m);
    }

    // ── Malayalam (മലയാളം) ────────────────────────────────────────────────────
    private static final Map<String, String> MALAYALAM_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(22);
        m.put("\u0D31\u0D4B\u0D21\u0D4D", "roads");                    // റോഡ്
        m.put("\u0D35\u0D46\u0D33\u0D4D\u0D33\u0D02", "water_supply"); // വെള്ളം
        m.put("\u0D35\u0D48\u0D26\u0D4D\u0D2F\u0D41\u0D24\u0D3F", "electricity"); // വൈദ്യുതി
        m.put("\u0D36\u0D41\u0D1A\u0D3F\u0D24\u0D4D\u0D35\u0D02", "sanitation"); // ശുചിത്വം
        m.put("\u0D06\u0D30\u0D4B\u0D17\u0D4D\u0D2F\u0D02", "health"); // ആരോഗ്യം
        m.put("\u0D06\u0D36\u0D41\u0D2A\u0D24\u0D4D\u0D30\u0D3F", "health"); // ആശുപത്രി
        m.put("\u0D35\u0D3F\u0D26\u0D4D\u0D2F\u0D3E\u0D2D\u0D4D\u0D2F\u0D3E\u0D38\u0D02", "education"); // വിദ്യാഭ്യാസം
        m.put("\u0D15\u0D4D\u0D30\u0D3F\u0D15\u0D4D\u0D15\u0D31\u0D4D\u0D31\u0D4D", "cricket"); // ക്രിക്കറ്റ്
        m.put("\u0D30\u0D3E\u0D37\u0D4D\u0D1F\u0D4D\u0D30\u0D40\u0D2F\u0D02", "politics"); // രാഷ്ട്രീയം
        m.put("\u0D24\u0D3F\u0D30\u0D1E\u0D4D\u0D1E\u0D46\u0D1F\u0D41\u0D2A\u0D4D\u0D2A\u0D4D", "politics"); // തിരഞ്ഞെടുപ്പ്
        m.put("\u0D09\u0D7D\u0D38\u0D35\u0D02", "festivals");          // ഉൽസവം
        m.put("\u0D2D\u0D15\u0D4D\u0D37\u0D23\u0D02", "food");         // ഭക്ഷണം
        m.put("\u0D1C\u0D4B\u0D32\u0D3F", "jobs");                     // ജോലി
        m.put("\u0D15\u0D3E\u0D32\u0D3E\u0D35\u0D38\u0D4D\u0D25", "weather"); // കാലാവസ്ഥ
        m.put("\u0D2E\u0D34", "weather");                               // മഴ
        m.put("\u0D38\u0D02\u0D17\u0D40\u0D24\u0D02", "music");        // സംഗീതം
        m.put("\u0D38\u0D3F\u0D28\u0D3F\u0D2E", "bollywood");          // സിനിമ
        MALAYALAM_MAP = Collections.unmodifiableMap(m);
    }

    // ── Punjabi / Gurmukhi (ਪੰਜਾਬੀ) ──────────────────────────────────────────
    private static final Map<String, String> PUNJABI_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(20);
        m.put("\u0A38\u0A5C\u0A15", "roads");                          // ਸੜਕ
        m.put("\u0A2A\u0A3E\u0A23\u0A40", "water_supply");             // ਪਾਣੀ
        m.put("\u0A2C\u0A3F\u0A1C\u0A32\u0A40", "electricity");        // ਬਿਜਲੀ
        m.put("\u0A38\u0A2B\u0A3C\u0A3E\u0A08", "sanitation");         // ਸਫ਼ਾਈ
        m.put("\u0A38\u0A3F\u0A39\u0A24", "health");                   // ਸਿਹਤ
        m.put("\u0A39\u0A38\u0A2A\u0A24\u0A3E\u0A32", "health");       // ਹਸਪਤਾਲ
        m.put("\u0A38\u0A3F\u0A71\u0A16\u0A3F\u0A06", "education");   // ਸਿੱਖਿਆ
        m.put("\u0A38\u0A15\u0A42\u0A32", "education");                // ਸਕੂਲ
        m.put("\u0A15\u0A4D\u0A30\u0A3F\u0A15\u0A1F", "cricket");     // ਕ੍ਰਿਕਟ
        m.put("\u0A30\u0A3E\u0A1C\u0A28\u0A40\u0A24\u0A40", "politics"); // ਰਾਜਨੀਤੀ
        m.put("\u0A1A\u0A4B\u0A23", "politics");                       // ਚੋਣ
        m.put("\u0A24\u0A3F\u0A09\u0A39\u0A3E\u0A30", "festivals");   // ਤਿਉਹਾਰ
        m.put("\u0A16\u0A3E\u0A23\u0A3E", "food");                     // ਖਾਣਾ
        m.put("\u0A28\u0A4C\u0A15\u0A30\u0A40", "jobs");               // ਨੌਕਰੀ
        m.put("\u0A2E\u0A4C\u0A38\u0A2E", "weather");                  // ਮੌਸਮ
        m.put("\u0A2E\u0A40\u0A02\u0A39", "weather");                  // ਮੀਂਹ (rain)
        m.put("\u0A38\u0A70\u0A17\u0A40\u0A24", "music");              // ਸੰਗੀਤ
        m.put("\u0A2B\u0A3C\u0A3F\u0A32\u0A2E", "bollywood");          // ਫ਼ਿਲਮ
        PUNJABI_MAP = Collections.unmodifiableMap(m);
    }

    // ── Odia (ଓଡ଼ିଆ) ─────────────────────────────────────────────────────────
    private static final Map<String, String> ODIA_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(18);
        m.put("\u0B30\u0B3E\u0B38\u0B4D\u0B24\u0B3E", "roads");        // ରାସ୍ତା
        m.put("\u0B2A\u0B3E\u0B23\u0B3F", "water_supply");             // ପାଣି
        m.put("\u0B2C\u0B3F\u0B26\u0B4D\u0B2F\u0B41\u0B24", "electricity"); // ବିଦ୍ୟୁତ
        m.put("\u0B38\u0B2B\u0B3E\u0B07", "sanitation");               // ସଫ଼ାଇ
        m.put("\u0B38\u0B4D\u0B35\u0B3E\u0B38\u0B4D\u0B25\u0B4D\u0B2F", "health"); // ସ୍ୱାସ୍ଥ୍ୟ
        m.put("\u0B36\u0B3F\u0B15\u0B4D\u0B37\u0B3E", "education");   // ଶିକ୍ଷା
        m.put("\u0B15\u0B4D\u0B30\u0B3F\u0B15\u0B47\u0B1F", "cricket"); // କ୍ରିକେଟ
        m.put("\u0B30\u0B3E\u0B1C\u0B28\u0B40\u0B24\u0B3F", "politics"); // ରାଜନୀତି
        m.put("\u0B09\u0B24\u0B4D\u0B38\u0B2C", "festivals");          // ଉତ୍ସବ
        m.put("\u0B16\u0B3E\u0B26\u0B4D\u0B2F", "food");              // ଖାଦ୍ୟ
        m.put("\u0B1A\u0B3E\u0B15\u0B3F\u0B30\u0B3F", "jobs");        // ଚାକିରି
        m.put("\u0B2C\u0B30\u0B4D\u0B37\u0B3E", "weather");           // ବର୍ଷା
        m.put("\u0B38\u0B02\u0B17\u0B40\u0B24", "music");             // ସଂଗୀତ
        m.put("\u0B1A\u0B33\u0B1A\u0B4D\u0B1A\u0B3F\u0B24\u0B4D\u0B30", "bollywood"); // ଚଳଚ୍ଚିତ୍ର
        ODIA_MAP = Collections.unmodifiableMap(m);
    }

    // ── Urdu (Arabic/Nastaliq script) ─────────────────────────────────────────
    private static final Map<String, String> URDU_MAP;
    static {
        Map<String, String> m = new LinkedHashMap<>(22);
        m.put("\u0633\u0691\u06A9", "roads");                          // سڑک
        m.put("\u067E\u0627\u0646\u06CC", "water_supply");             // پانی
        m.put("\u0628\u062C\u0644\u06CC", "electricity");              // بجلی
        m.put("\u0635\u0641\u0627\u0626\u06CC", "sanitation");         // صفائی
        m.put("\u0635\u062D\u062A", "health");                         // صحت
        m.put("\u06C1\u0633\u067E\u062A\u0627\u0644", "health");       // ہسپتال
        m.put("\u062A\u0639\u0644\u06CC\u0645", "education");          // تعلیم
        m.put("\u0627\u0633\u06A9\u0648\u0644", "education");          // اسکول
        m.put("\u06A9\u0631\u06A9\u0679", "cricket");                  // کرکٹ
        m.put("\u0633\u06CC\u0627\u0633\u062A", "politics");           // سیاست
        m.put("\u0627\u0646\u062A\u062E\u0627\u0628\u0627\u062A", "politics"); // انتخابات
        m.put("\u062A\u06C1\u0648\u0627\u0631", "festivals");          // تہوار
        m.put("\u06A9\u06BE\u0627\u0646\u0627", "food");               // کھانا
        m.put("\u0645\u0644\u0627\u0632\u0645\u062A", "jobs");         // ملازمت
        m.put("\u0645\u0648\u0633\u0645", "weather");                  // موسم
        m.put("\u0628\u0627\u0631\u0634", "weather");                  // بارش
        m.put("\u0645\u0648\u0633\u06CC\u0642\u06CC", "music");        // موسیقی
        m.put("\u0641\u0644\u0645", "bollywood");                      // فلم
        m.put("\u067E\u06CC\u0633\u06DB", "finance");                  // پیسے
        URDU_MAP = Collections.unmodifiableMap(m);
    }

    // ── Master merged native-script map ───────────────────────────────────────

    private static final Map<String, String> ALL_LANG_KEYWORD_MAP;
    static {
        Map<String, String> combined = new HashMap<>(350);
        combined.putAll(HINDI_MAP);
        combined.putAll(MARATHI_MAP);
        combined.putAll(BENGALI_MAP);
        combined.putAll(TELUGU_MAP);
        combined.putAll(TAMIL_MAP);
        combined.putAll(GUJARATI_MAP);
        combined.putAll(KANNADA_MAP);
        combined.putAll(MALAYALAM_MAP);
        combined.putAll(PUNJABI_MAP);
        combined.putAll(ODIA_MAP);
        combined.putAll(URDU_MAP);
        ALL_LANG_KEYWORD_MAP = Collections.unmodifiableMap(combined);
    }

    /**
     * FIX MEMORY PRESSURE: resolveToken() previously did an O(n) full scan of
     * ROMAN_KEYWORD_MAP (~800 entries) twice per unmatched token to handle compound
     * words like "water_supply_problem". At high traffic with ~100 tokens/post and
     * most tokens missing the direct hash-lookup, this generates millions of
     * Map.Entry iterator allocations per second.
     *
     * Fix: at class-load time we pre-build PREFIX_INDEX — a Map from every key in
     * ROMAN_KEYWORD_MAP to its topic value, plus one entry per key for every
     * possible prefix of that key (down to length 4). resolveToken() now does a
     * single O(1) lookup into PREFIX_INDEX instead of a full iteration.
     *
     * Space cost: ~800 keys × average 3 prefix entries = ~2 400 extra map entries,
     * each a short String. Negligible vs the per-request GC savings.
     */
    private static final Map<String, String> PREFIX_INDEX;
    static {
        Map<String, String> idx = new HashMap<>(ROMAN_KEYWORD_MAP.size() * 4);
        for (Map.Entry<String, String> e : ROMAN_KEYWORD_MAP.entrySet()) {
            String key   = e.getKey();
            String topic = e.getValue();
            idx.put(key, topic); // exact match
            // Add prefix entries (min length 4 to avoid false positives on short tokens)
            for (int len = Math.max(4, key.length() / 2); len < key.length(); len++) {
                idx.putIfAbsent(key.substring(0, len), topic);
            }
        }
        PREFIX_INDEX = Collections.unmodifiableMap(idx);
    }

    // ── Location tokens to suppress ───────────────────────────────────────────

    private static final Set<String> LOCATION_TOKENS = Set.of(
            "mumbai","delhi","bangalore","bengaluru","hyderabad","chennai","kolkata","pune",
            "ahmedabad","jaipur","lucknow","kanpur","nagpur","indore","thane","bhopal",
            "visakhapatnam","patna","vadodara","ghaziabad","ludhiana","agra","nashik",
            "faridabad","meerut","rajkot","varanasi","srinagar","aurangabad","dhanbad",
            "amritsar","navi_mumbai","allahabad","prayagraj","howrah","jabalpur","coimbatore",
            "vijayawada","jodhpur","madurai","raipur","kota","chandigarh","noida","gurgaon",
            "maharashtra","gujarat","karnataka","tamilnadu","andhra","telangana","rajasthan",
            "up","uttarpradesh","mp","madhyapradesh","bihar","jharkhand","odisha","chhattisgarh",
            "westbengal","kerala","himachal","uttarakhand","goa","assam","punjab","haryana",
            "india","bharat","desh","city","state","pincode","area","local","near","nearby"
    );

    /**
     * Splits on ASCII punctuation/whitespace ONLY.
     * Non-ASCII (Devanagari, Tamil, Arabic, etc.) characters are NOT treated
     * as separators — native-script words remain intact for native-map lookup.
     */
    private static final Pattern WORD_SPLIT = Pattern.compile("[ \\t\\n\\r,#@_.\\-]+");

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Extracts topic → max_strength map for a post.
     * Call once per post; result is cached in HLIGFeedService.ptfCache.
     */
    public Map<String, Double> extract(SocialPost post) {
        Map<String, Double> result = new HashMap<>();

        // 1. Hashtags — highest signal strength
        if (post.hasHashtags()) {
            for (String tag : post.getHashtagsList()) {
                String raw = tag.replaceAll("^#", "").trim();
                // Native-script lookup on raw Unicode string FIRST
                String nativeTopic = ALL_LANG_KEYWORD_MAP.get(raw);
                if (nativeTopic != null) {
                    result.merge(nativeTopic, HASHTAG_STRENGTH, Math::max);
                    continue;
                }
                String topic = resolveToken(normalise(raw));
                if (topic != null) result.merge(topic, HASHTAG_STRENGTH, Math::max);
            }
        }

        // 2. Community category
        if (post.getCommunity() != null && post.getCommunity().getCategory() != null) {
            String topic = resolveToken(normalise(post.getCommunity().getCategory()));
            if (topic != null) result.merge(topic, CATEGORY_STRENGTH, Math::max);
        }

        // 3. Community tags
        if (post.getCommunity() != null && post.getCommunity().getTags() != null) {
            for (String t : post.getCommunity().getTags().split(",")) {
                String raw = t.trim();
                String nativeTopic = ALL_LANG_KEYWORD_MAP.get(raw);
                if (nativeTopic != null) {
                    result.merge(nativeTopic, COMM_TAG_STRENGTH, Math::max);
                    continue;
                }
                String topic = resolveToken(normalise(raw));
                if (topic != null) result.merge(topic, COMM_TAG_STRENGTH, Math::max);
            }
        }

        // 4. Poll question
        if (post.hasPoll() && post.getPoll() != null && post.getPoll().getQuestion() != null) {
            extractFromText(post.getPoll().getQuestion(), POLL_STRENGTH, result);
        }

        // 5. Post body
        if (post.getContent() != null) {
            extractFromText(post.getContent(), CONTENT_STRENGTH, result);
        }

        return result;
    }

    /** Fast path: hashtags only. Used for lightweight scroll-past signals. */
    public Map<String, Double> extractFromHashtagsOnly(SocialPost post) {
        Map<String, Double> result = new HashMap<>();
        if (!post.hasHashtags()) return result;
        for (String tag : post.getHashtagsList()) {
            String raw = tag.replaceAll("^#", "").trim();
            String nativeTopic = ALL_LANG_KEYWORD_MAP.get(raw);
            if (nativeTopic != null) { result.put(nativeTopic, HASHTAG_STRENGTH); continue; }
            String topic = resolveToken(normalise(raw));
            if (topic != null) result.put(topic, HASHTAG_STRENGTH);
        }
        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void extractFromText(String text, double strength, Map<String, Double> result) {
        if (text == null || text.isBlank()) return;
        for (String token : WORD_SPLIT.split(text)) {
            if (token.isBlank()) continue;
            // Raw native-script lookup before normalisation
            String nativeTopic = ALL_LANG_KEYWORD_MAP.get(token.trim());
            if (nativeTopic != null) {
                result.merge(nativeTopic, strength, Math::max);
                continue;
            }
            String topic = resolveToken(normalise(token.toLowerCase()));
            if (topic != null) result.merge(topic, strength, Math::max);
        }
    }

    public String resolveToken(String token) {
        if (token == null || token.isBlank() || token.length() < 2) return null;
        if (LOCATION_TOKENS.contains(token)) return null;

        // FIX MEMORY PRESSURE: replaced the O(n) full-scan of ROMAN_KEYWORD_MAP with
        // a single O(1) lookup into PREFIX_INDEX (built at class-load time).
        // Exact match — fastest path
        String topic = PREFIX_INDEX.get(token);
        if (topic != null) return topic;

        // Compound suffix: "water_supply_problem" — strip trailing segments one by one
        // until we hit a known prefix. Max 3 strips to avoid runaway loops.
        String t = token;
        for (int i = 0; i < 3; i++) {
            int lastUnderscore = t.lastIndexOf('_');
            if (lastUnderscore < 4) break; // prefix too short to be meaningful
            t = t.substring(0, lastUnderscore);
            topic = PREFIX_INDEX.get(t);
            if (topic != null) return topic;
        }
        return null;
    }

    /**
     * Normalises a Latin/Roman token.
     * Strips diacritics from the ASCII range only.
     * Non-ASCII characters (Indian scripts) are left INTACT.
     */
    private String normalise(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase().trim();
        String nfd   = Normalizer.normalize(lower, Normalizer.Form.NFD);
        // Keep: a-z, 0-9, underscore, and everything outside ASCII (native scripts)
        String clean = nfd.replaceAll("[\\x20-\\x7E&&[^a-z0-9_]]", "");
        return clean.replaceAll("_{2,}", "_").replaceAll("^_|_$", "");
    }

    private static void put(Map<String, String> m, String topic, String... keywords) {
        for (String kw : keywords) m.put(kw, topic);
    }
}
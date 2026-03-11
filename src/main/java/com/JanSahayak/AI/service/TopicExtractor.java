package com.JanSahayak.AI.service;

import com.JanSahayak.AI.model.SocialPost;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Component
public class TopicExtractor {

    // ── Strength constants ────────────────────────────────────────────────────

    private static final double HASHTAG_STRENGTH   = 1.00;
    private static final double CATEGORY_STRENGTH  = 0.90;
    private static final double COMM_TAG_STRENGTH  = 0.75;
    private static final double POLL_STRENGTH      = 0.70;
    private static final double CONTENT_STRENGTH   = 0.60;

    private static final Map<String, String> KEYWORD_MAP;
    static {
        Map<String, String> m = new HashMap<>(700);

        // ─ Civic / Infrastructure ─
        put(m, "roads",    "roads", "road","pothole","potholes","traffic","highway","flyover",
                "underpass","signal","zebra","divider","sidewalk","footpath","roadwork","nala",
                "drain","drainage","gutter","manhole","sewage","sewer");
        put(m, "water_supply", "water","pani","paani","pipeline","leakage","tap","borewell",
                "groundwater","tanker","waterboard","jal","jalboard","nalajal");
        put(m, "electricity", "electricity","bijli","power","powercut","loadShedding","transformer",
                "meter","msedcl","bescom","tneb","streetlight","light","wiring","short_circuit");
        put(m, "sanitation", "garbage","kachra","dustbin","municipal","cleaning","sweeper",
                "solid_waste","landfill","compost","segregation","wet_waste","dry_waste","bhangaar");
        put(m, "parks",     "park","garden","playground","tree","green","plant","nursery",
                "jogging","walking","recreation");
        put(m, "housing",   "housing","flat","apartment","building","construction","demolition",
                "slum","jhuggi","eviction","property","real_estate","plot","rent","tenant","landlord");
        put(m, "public_transport", "bus","auto","rickshaw","metro","local","train","railway",
                "ola","uber","rapido","cab","taxi","commute","station","depot");

        // ─ Local Governance ─
        put(m, "local_governance", "corporator","ward","municipality","nagar_palika","pmc","bmc",
                "mcgm","bbmp","complaint","grievance","petition","mayor","council","mla","mp",
                "sarpanch","panchayat","gram_sabha","rti","government","sarkar");
        put(m, "corruption",  "corruption","bhrashtachar","bribery","scam","fraud","encroachment",
                "illegal","nexus","kickback","tenderscam");

        // ─ Politics ─
        put(m, "politics",   "politics","election","vote","party","bjp","congress","aap","shiv_sena",
                "ncp","dmk","tmc","sp","bsp","rjd","manifesto","rally","campaign","candidate",
                "ballot","polling","booth","result","seat","constituency","vidhansabha","loksabha");
        put(m, "national_news","modi","government","parliament","budget","policy","scheme","yojana",
                "law","court","supreme_court","high_court","verdict","judgement","constitutional");

        // ─ Cricket / IPL (India's #1 interest) ─
        put(m, "cricket",    "cricket","ipl","t20","odi","test","virat","rohit","dhoni","sachin",
                "bumrah","shami","jadeja","hardik","rishabh","sanju","sixer","wicket","century",
                "boundary","runout","drs","umpire","stadium","pitch","over","inning","bcci");
        put(m, "ipl",        "ipl","rcb","mi","csk","kkr","srh","rr","dc","gt","lsg","pbks",
                "mumbai_indians","chennai","kolkata","hyderabad","bangalore","franchise","auction",
                "retention","mega_auction","points_table","qualifier","eliminator","final");

        // ─ Other Sports ─
        put(m, "football",   "football","soccer","fifa","isl","premier_league","ronaldo","messi",
                "mbappe","mohunbagan","eastbengal","goa_fc","goal","freekick","penalty","offside");
        put(m, "kabaddi",    "kabaddi","pkl","pro_kabaddi","raider","tackle","super_raid");
        put(m, "badminton",  "badminton","pv_sindhu","saina","shuttlecock","smash","bwf");
        put(m, "wrestling",  "wrestling","bajrang","vinesh","wwe","ufc","dangal");
        put(m, "chess",      "chess","pragg","nihal","anand","fide","worldchampionship");
        put(m, "fitness",    "fitness","gym","workout","yoga","zumba","crossfit","marathon",
                "running","cycling","swimming","diet","weight","protein","cardio");

        // ─ Bollywood / Entertainment ─
        put(m, "bollywood",  "bollywood","film","movie","cinema","trailer","release","actor",
                "actress","director","srk","shahrukh","salman","aamir","ranveer","deepika",
                "alia","kareena","priyanka","anushka","katrina","hrithik","ranbir","jawan",
                "pathaan","animal","dunki","kgf","bahubali","ott","netflix","amazon","hotstar",
                "disney","zee5","sonyliv");
        put(m, "music",      "music","song","gana","album","singer","rapper","rap","hip_hop",
                "pop","classic","devotional","arijit","atif","diljit","badshah","honey_singh",
                "ar_rahman","spotify","gaana","jiosaavn","youtube_music");
        put(m, "webseries",  "webseries","series","season","episode","binge","mirzapur",
                "scam1992","panchayat","delhi_crime","brahmastra","tvf","scam","money_heist",
                "squidgame","breaking_bad","game_of_thrones");
        put(m, "comedy",     "comedy","standup","roast","meme","reel","funny","viral","joke",
                "troll","parody","satire");

        // ─ Food ─
        put(m, "food",       "food","khana","restaurant","hotel","dhaba","cafe","street_food",
                "biryani","pizza","burger","dosa","idli","samosa","chaat","pav_bhaji","vada_pav",
                "thali","recipe","cooking","baking","chef","zomato","swiggy","food_delivery");
        put(m, "vegetarian", "veg","vegetarian","vegan","sattvic","jain_food");
        put(m, "regional_food","maharashtrian","gujarati_food","punjabi_food","south_indian",
                "bengali_food","rajasthani","hyderabadi","kolhapuri","goan","kerala_food");

        // ─ Technology ─
        put(m, "technology", "tech","technology","startup","ai","ml","machine_learning","chatgpt",
                "openai","gemini","llm","coding","programming","developer","software","app",
                "mobile","android","ios","laptop","computer","gadget","review","unboxing");
        put(m, "gaming",     "gaming","game","bgmi","freefire","pubg","valorant","minecraft",
                "gta","cod","esports","pc_gaming","console","ps5","xbox","nintendo","steam");
        put(m, "crypto",     "crypto","bitcoin","ethereum","nft","blockchain","defi","web3",
                "altcoin","binance","coinbase","trading","pump","dump","hodl","wallet");

        // ─ Finance / Economy ─
        put(m, "finance",    "finance","money","stock","market","sensex","nifty","mutual_fund",
                "sip","investment","portfolio","shares","equity","ipo","sebi","rbi","loan","emi",
                "bank","gold","silver","inflation","gdp","economy","budget");
        put(m, "jobs",       "jobs","job","career","hiring","recruitment","interview","resume",
                "internship","fresher","salary","appraisal","layoff","wfh","remote","startup");

        // ─ Education ─
        put(m, "education",  "education","school","college","university","exam","result","neet",
                "jee","upsc","mpsc","gate","cat","clat","board","cbse","ssc","railway","bank_exam",
                "coaching","study","tuition","scholarship","ugc","aicte","iit","nit","iim");

        // ─ Health ─
        put(m, "health",     "health","hospital","doctor","medicine","disease","covid","dengue",
                "malaria","fever","vaccination","ayurveda","homeopathy","mental_health","therapy",
                "depression","anxiety","yoga_health","diet","nutrition","blood_donation","icu");

        // ─ Environment / Weather ─
        put(m, "environment","pollution","air_quality","aqi","smog","flood","drought","climate",
                "global_warming","carbon","forest","wildlife","tiger","leopard","conservation",
                "clean_energy","solar","wind_energy","ev","electric_vehicle");
        put(m, "weather",    "rain","monsoon","flood","heatwave","cold","winter","fog","cyclone",
                "earthquake","tsunami","disaster","relief","ndrf");

        // ─ Religion / Culture / Festivals ─
        put(m, "religion",   "temple","mandir","masjid","church","gurudwara","prayer","puja",
                "namaz","mass","holy","divine","god","bhagwan","allah","jesus","guru");
        put(m, "festivals",  "festival","diwali","holi","eid","christmas","navratri","ganesh",
                "durga","onam","pongal","baisakhi","ugadi","dussehra","raksha","janmashtami",
                "independence_day","republic_day","bharat");
        put(m, "culture",    "culture","tradition","heritage","art","handicraft","folk","classical",
                "dance","bharatnatyam","kathak","odissi","classical_music","folk_music");

        // ─ Women / Social ─
        put(m, "women",      "women","mahila","girl","feminist","equality","safety","harassment",
                "metoo","domestic_violence","empowerment","self_help","ngo");
        put(m, "social",     "social","community","ngo","volunteer","donation","charity",
                "helpline","welfare","senior_citizen","disability","inclusion");

        // ─ Travel / Local Discovery ─
        put(m, "travel",     "travel","trip","tour","tourism","holiday","vacation","trek",
                "backpack","hotel_stay","resort","beach","mountain","temple_tour","heritage_walk",
                "makemytrip","goibibo","irctc","airport","flight");
        put(m, "local_events","event","concert","exhibition","workshop","seminar","meetup",
                "fair","mela","yatra","procession","march","protest");

        // ─ Animals / Pets ─
        put(m, "pets",       "dog","cat","pet","puppy","kitten","animal","stray","shelter",
                "adoption","rescue","paws","feeder","animal_rights","spca");

        KEYWORD_MAP = Collections.unmodifiableMap(m);
    }

    /** Location tokens to suppress (city/district names carry no interest signal). */
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

    /** Devanagari → common Roman transliteration map for popular civic hashtags. */
    private static final Map<String, String> HINDI_HASHTAG_MAP = Map.of(
            "सड़क",    "roads",
            "पानी",    "water_supply",
            "बिजली",   "electricity",
            "सफाई",    "sanitation",
            "स्वास्थ्य", "health",
            "शिक्षा",  "education",
            "खेल",     "sports",
            "क्रिकेट", "cricket",
            "राजनीति", "politics",
            "त्योहार", "festivals"
    );

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,#@_.\\-]+");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Main entry point. Returns map of topic → max_strength for the post.
     * Topics with strength 0 are excluded.
     */
    public Map<String, Double> extract(SocialPost post) {
        Map<String, Double> result = new HashMap<>();

        // 1. Hashtags (highest weight)
        if (post.hasHashtags()) {
            for (String tag : post.getHashtagsList()) {
                String raw = tag.replaceAll("^#", "").trim();
                // Check Hindi Devanagari map BEFORE normalise() which may alter Unicode
                String directHindi = HINDI_HASHTAG_MAP.get(raw);
                if (directHindi != null) {
                    result.merge(directHindi, HASHTAG_STRENGTH, Math::max);
                    continue;
                }
                String clean = normalise(raw);
                String topic = resolveToken(clean);
                if (topic != null) {
                    result.merge(topic, HASHTAG_STRENGTH, Math::max);
                }
            }
        }

        // 2. Community category
        if (post.getCommunity() != null && post.getCommunity().getCategory() != null) {
            String topic = resolveToken(normalise(post.getCommunity().getCategory()));
            if (topic != null) result.merge(topic, CATEGORY_STRENGTH, Math::max);
        }

        // 3. Community tags (comma-separated, 500-char field)
        if (post.getCommunity() != null && post.getCommunity().getTags() != null) {
            for (String t : post.getCommunity().getTags().split(",")) {
                String topic = resolveToken(normalise(t.trim()));
                if (topic != null) result.merge(topic, COMM_TAG_STRENGTH, Math::max);
            }
        }

        // 4. Poll question + options (if present)
        if (post.hasPoll() && post.getPoll() != null) {
            String pollText = (post.getPoll().getQuestion() != null ? post.getPoll().getQuestion() : "");
            for (String word : WORD_SPLIT.split(pollText.toLowerCase())) {
                String topic = resolveToken(normalise(word));
                if (topic != null) result.merge(topic, POLL_STRENGTH, Math::max);
            }
        }

        // 5. Content keywords
        if (post.getContent() != null) {
            String lc = post.getContent().toLowerCase();
            for (String word : WORD_SPLIT.split(lc)) {
                String topic = resolveToken(normalise(word));
                if (topic != null) result.merge(topic, CONTENT_STRENGTH, Math::max);
            }
        }

        return result;
    }

    /** Fast path: hashtags only (used when updating interest profile, not scoring). */
    public Map<String, Double> extractFromHashtagsOnly(SocialPost post) {
        Map<String, Double> result = new HashMap<>();
        if (!post.hasHashtags()) return result;
        for (String tag : post.getHashtagsList()) {
            String clean = normalise(tag.replaceAll("^#", ""));
            String topic = resolveToken(clean);
            if (topic != null) result.put(topic, HASHTAG_STRENGTH);
        }
        return result;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Resolves a token to a canonical topic name.
     * Supports:
     *  - Direct keyword lookup
     *  - Hindi Devanagari hashtag lookup
     *  - Suppression of location tokens
     */
    public String resolveToken(String token) {
        if (token == null || token.isBlank() || token.length() < 2) return null;
        if (LOCATION_TOKENS.contains(token)) return null;

        // Direct lookup
        String topic = KEYWORD_MAP.get(token);
        if (topic != null) return topic;

        // Hindi script lookup
        String fromHindi = HINDI_HASHTAG_MAP.get(token);
        if (fromHindi != null) return fromHindi;

        // Compound keyword: "water_supply_problem" → check prefix
        for (Map.Entry<String, String> e : KEYWORD_MAP.entrySet()) {
            if (token.startsWith(e.getKey()) || e.getKey().startsWith(token)) {
                return e.getValue();
            }
        }

        return null;
    }

    private String normalise(String s) {
        if (s == null) return "";
        // Lowercase, remove diacritics from Romanised Indic text
        String ascii = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}\\p{InDevanagari}]", "");
        return ascii.replaceAll("[^a-z0-9_\\p{InDevanagari}]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
    }

    // Helper for static initialiser
    private static void put(Map<String, String> m, String topic, String... keywords) {
        for (String kw : keywords) m.put(kw, topic);
    }
}
package com.blackfalcon.poiplaces;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in list of major cities for coarse offline place names. Coordinates are approximate
 * (about 0.01 to 0.1 degree). Format: name;lat;lon;country;c (city) or t (town).
 */
public class Cities {

    private static final String[] D = {
            // Pakistan
            "Islamabad;33.6844;73.0479;PK;c", "Rawalpindi;33.5651;73.0169;PK;c", "Lahore;31.5497;74.3436;PK;c",
            "Karachi;24.8607;67.0011;PK;c", "Faisalabad;31.4504;73.1350;PK;c", "Multan;30.1575;71.5249;PK;c",
            "Peshawar;34.0151;71.5249;PK;c", "Quetta;30.1798;66.9750;PK;c", "Hyderabad;25.3960;68.3578;PK;c",
            "Gujranwala;32.1877;74.1945;PK;c", "Sialkot;32.4945;74.5229;PK;c", "Sargodha;32.0740;72.6861;PK;c",
            "Bahawalpur;29.3956;71.6836;PK;c", "Sukkur;27.7052;68.8574;PK;c", "Larkana;27.5589;68.2264;PK;c",
            "Mardan;34.1986;72.0404;PK;c", "Nowshera;34.0153;71.9747;PK;t", "Risalpur;34.0600;71.9900;PK;t",
            "Abbottabad;34.1688;73.2215;PK;c", "Mansehra;34.3333;73.2000;PK;t", "Mingora;34.7717;72.3600;PK;c",
            "Kohat;33.5869;71.4414;PK;c", "Bannu;32.9889;70.6056;PK;c", "Dera Ismail Khan;31.8626;70.9019;PK;c",
            "Charsadda;34.1453;71.7308;PK;t", "Swabi;34.1200;72.4700;PK;t", "Haripur;33.9946;72.9337;PK;t",
            "Chitral;35.8518;71.7864;PK;t", "Gilgit;35.9208;74.3080;PK;c", "Skardu;35.2971;75.6333;PK;t",
            "Muzaffarabad;34.3700;73.4711;PK;c", "Mirpur;33.1480;73.7510;PK;t", "Jhelum;32.9345;73.7310;PK;t",
            "Gujrat;32.5731;74.0750;PK;c", "Okara;30.8081;73.4458;PK;t", "Sahiwal;30.6682;73.1114;PK;c",
            "Rahim Yar Khan;28.4202;70.2952;PK;c", "Dera Ghazi Khan;30.0489;70.6455;PK;c",
            "Sheikhupura;31.7167;73.9850;PK;c", "Jhang;31.2681;72.3181;PK;t", "Kasur;31.1187;74.4500;PK;t",
            "Mianwali;32.5839;71.5370;PK;t", "Attock;33.7660;72.3609;PK;t", "Taxila;33.7460;72.8397;PK;t",
            "Wah Cantt;33.7715;72.7511;PK;t", "Murree;33.9070;73.3943;PK;t", "Khuzdar;27.8000;66.6167;PK;t",
            "Gwadar;25.1264;62.3225;PK;t", "Turbat;26.0012;63.0544;PK;t", "Zhob;31.3410;69.4481;PK;t",
            "Mirpur Khas;25.5276;69.0111;PK;t", "Nawabshah;26.2442;68.4100;PK;t", "Jacobabad;28.2769;68.4514;PK;t",
            "Thatta;24.7461;67.9240;PK;t", "Parachinar;33.9000;70.1000;PK;t", "Miranshah;32.9747;70.0711;PK;t",
            "Timergara;34.8280;71.8400;PK;t",
            // Afghanistan
            "Kabul;34.5553;69.2075;AF;c", "Jalalabad;34.4415;70.4361;AF;c", "Kandahar;31.6289;65.7372;AF;c",
            "Herat;34.3529;62.2040;AF;c", "Mazar-i-Sharif;36.7090;67.1109;AF;c",
            // India and neighbors
            "Delhi;28.6139;77.2090;IN;c", "Mumbai;19.0760;72.8777;IN;c", "Kolkata;22.5726;88.3639;IN;c",
            "Chennai;13.0827;80.2707;IN;c", "Bengaluru;12.9716;77.5946;IN;c", "Hyderabad;17.3850;78.4867;IN;c",
            "Ahmedabad;23.0225;72.5714;IN;c", "Amritsar;31.6340;74.8723;IN;c", "Srinagar;34.0837;74.7973;IN;c",
            "Leh;34.1526;77.5770;IN;t", "Jaipur;26.9124;75.7873;IN;c", "Lucknow;26.8467;80.9462;IN;c",
            "Pune;18.5204;73.8567;IN;c", "Dhaka;23.8103;90.4125;BD;c", "Kathmandu;27.7172;85.3240;NP;c",
            "Colombo;6.9271;79.8612;LK;c",
            // Iran and Central Asia
            "Tehran;35.6892;51.3890;IR;c", "Mashhad;36.2605;59.6168;IR;c", "Isfahan;32.6546;51.6680;IR;c",
            "Shiraz;29.5918;52.5837;IR;c", "Tabriz;38.0962;46.2738;IR;c", "Tashkent;41.2995;69.2401;UZ;c",
            "Samarkand;39.6270;66.9750;UZ;c", "Dushanbe;38.5598;68.7870;TJ;c", "Almaty;43.2220;76.8512;KZ;c",
            "Astana;51.1694;71.4491;KZ;c", "Bishkek;42.8746;74.5698;KG;c", "Ashgabat;37.9601;58.3261;TM;c",
            // Middle East
            "Mecca;21.3891;39.8579;SA;c", "Medina;24.5247;39.5692;SA;c", "Jeddah;21.4858;39.1925;SA;c",
            "Riyadh;24.7136;46.6753;SA;c", "Dammam;26.4207;50.0888;SA;c", "Dubai;25.2048;55.2708;AE;c",
            "Abu Dhabi;24.4539;54.3773;AE;c", "Doha;25.2854;51.5310;QA;c", "Kuwait City;29.3759;47.9774;KW;c",
            "Manama;26.2285;50.5860;BH;c", "Muscat;23.5880;58.3829;OM;c", "Baghdad;33.3152;44.3661;IQ;c",
            "Basra;30.5085;47.7804;IQ;c", "Damascus;33.5138;36.2765;SY;c", "Aleppo;36.2021;37.1343;SY;c",
            "Beirut;33.8938;35.5018;LB;c", "Amman;31.9454;35.9284;JO;c", "Jerusalem;31.7683;35.2137;;c",
            "Tel Aviv;32.0853;34.7818;IL;c", "Istanbul;41.0082;28.9784;TR;c", "Ankara;39.9334;32.8597;TR;c",
            "Izmir;38.4237;27.1428;TR;c", "Konya;37.8746;32.4932;TR;c", "Sanaa;15.3694;44.1910;YE;c",
            "Aden;12.7855;45.0187;YE;c", "Baku;40.4093;49.8671;AZ;c", "Tbilisi;41.7151;44.8271;GE;c",
            "Yerevan;40.1792;44.4991;AM;c",
            // Africa
            "Cairo;30.0444;31.2357;EG;c", "Alexandria;31.2001;29.9187;EG;c", "Tripoli;32.8872;13.1913;LY;c",
            "Tunis;36.8065;10.1815;TN;c", "Algiers;36.7538;3.0588;DZ;c", "Casablanca;33.5731;-7.5898;MA;c",
            "Rabat;34.0209;-6.8416;MA;c", "Khartoum;15.5007;32.5599;SD;c", "Addis Ababa;9.0300;38.7400;ET;c",
            "Nairobi;-1.2921;36.8219;KE;c", "Dar es Salaam;-6.7924;39.2083;TZ;c", "Lagos;6.5244;3.3792;NG;c",
            "Abuja;9.0765;7.3986;NG;c", "Accra;5.6037;-0.1870;GH;c", "Dakar;14.7167;-17.4677;SN;c",
            "Kinshasa;-4.4419;15.2663;CD;c", "Luanda;-8.8390;13.2894;AO;c", "Johannesburg;-26.2041;28.0473;ZA;c",
            "Cape Town;-33.9249;18.4241;ZA;c", "Mogadishu;2.0469;45.3182;SO;c", "Kampala;0.3476;32.5825;UG;c",
            // Europe
            "London;51.5074;-0.1278;GB;c", "Manchester;53.4808;-2.2426;GB;c", "Paris;48.8566;2.3522;FR;c",
            "Berlin;52.5200;13.4050;DE;c", "Madrid;40.4168;-3.7038;ES;c", "Barcelona;41.3851;2.1734;ES;c",
            "Rome;41.9028;12.4964;IT;c", "Milan;45.4642;9.1900;IT;c", "Moscow;55.7558;37.6173;RU;c",
            "Saint Petersburg;59.9311;30.3609;RU;c", "Kyiv;50.4501;30.5234;UA;c", "Warsaw;52.2297;21.0122;PL;c",
            "Vienna;48.2082;16.3738;AT;c", "Amsterdam;52.3676;4.9041;NL;c", "Brussels;50.8503;4.3517;BE;c",
            "Lisbon;38.7223;-9.1393;PT;c", "Athens;37.9838;23.7275;GR;c", "Stockholm;59.3293;18.0686;SE;c",
            "Oslo;59.9139;10.7522;NO;c", "Copenhagen;55.6761;12.5683;DK;c", "Helsinki;60.1699;24.9384;FI;c",
            "Dublin;53.3498;-6.2603;IE;c", "Prague;50.0755;14.4378;CZ;c", "Budapest;47.4979;19.0402;HU;c",
            "Bucharest;44.4268;26.1025;RO;c", "Sofia;42.6977;23.3219;BG;c", "Belgrade;44.7866;20.4489;RS;c",
            "Zurich;47.3769;8.5417;CH;c", "Sarajevo;43.8563;18.4131;BA;c", "Tirana;41.3275;19.8187;AL;c",
            // East Asia
            "Beijing;39.9042;116.4074;CN;c", "Shanghai;31.2304;121.4737;CN;c", "Guangzhou;23.1291;113.2644;CN;c",
            "Shenzhen;22.5431;114.0579;CN;c", "Chengdu;30.5728;104.0668;CN;c", "Urumqi;43.8256;87.6168;CN;c",
            "Kashgar;39.4704;75.9898;CN;c", "Lhasa;29.6500;91.1000;CN;c", "Hong Kong;22.3193;114.1694;HK;c",
            "Tokyo;35.6762;139.6503;JP;c", "Osaka;34.6937;135.5023;JP;c", "Seoul;37.5665;126.9780;KR;c",
            "Pyongyang;39.0392;125.7625;KP;c", "Taipei;25.0330;121.5654;TW;c", "Ulaanbaatar;47.8864;106.9057;MN;c",
            // Southeast Asia
            "Bangkok;13.7563;100.5018;TH;c", "Hanoi;21.0285;105.8542;VN;c", "Ho Chi Minh City;10.8231;106.6297;VN;c",
            "Jakarta;-6.2088;106.8456;ID;c", "Kuala Lumpur;3.1390;101.6869;MY;c", "Singapore;1.3521;103.8198;SG;c",
            "Manila;14.5995;120.9842;PH;c", "Yangon;16.8409;96.1735;MM;c", "Phnom Penh;11.5564;104.9282;KH;c",
            "Vientiane;17.9757;102.6331;LA;c",
            // Americas
            "New York;40.7128;-74.0060;US;c", "Los Angeles;34.0522;-118.2437;US;c", "Chicago;41.8781;-87.6298;US;c",
            "Houston;29.7604;-95.3698;US;c", "Washington;38.9072;-77.0369;US;c", "San Francisco;37.7749;-122.4194;US;c",
            "Miami;25.7617;-80.1918;US;c", "Dallas;32.7767;-96.7970;US;c", "Seattle;47.6062;-122.3321;US;c",
            "Toronto;43.6532;-79.3832;CA;c", "Vancouver;49.2827;-123.1207;CA;c", "Montreal;45.5017;-73.5673;CA;c",
            "Ottawa;45.4215;-75.6972;CA;c", "Mexico City;19.4326;-99.1332;MX;c", "Havana;23.1136;-82.3666;CU;c",
            "Bogota;4.7110;-74.0721;CO;c", "Lima;-12.0464;-77.0428;PE;c", "Santiago;-33.4489;-70.6693;CL;c",
            "Buenos Aires;-34.6037;-58.3816;AR;c", "Sao Paulo;-23.5505;-46.6333;BR;c",
            "Rio de Janeiro;-22.9068;-43.1729;BR;c", "Brasilia;-15.7939;-47.8828;BR;c", "Caracas;10.4806;-66.9036;VE;c",
            "Quito;-0.1807;-78.4678;EC;c", "La Paz;-16.4897;-68.1193;BO;c",
            // Oceania
            "Sydney;-33.8688;151.2093;AU;c", "Melbourne;-37.8136;144.9631;AU;c", "Brisbane;-27.4698;153.0251;AU;c",
            "Perth;-31.9505;115.8605;AU;c", "Auckland;-36.8485;174.7633;NZ;c", "Wellington;-41.2866;174.7756;NZ;c"
    };

    private static List<Place> cache = null;

    /** All built-in cities as Place objects (kind "settlement", sub "city" or "town"). */
    public static synchronized List<Place> all() {
        if (cache != null) return cache;
        List<Place> l = new ArrayList<Place>();
        for (String line : D) {
            String[] f = line.split(";", -1);
            if (f.length < 5) continue;
            try {
                Place p = new Place(f[0], Double.parseDouble(f[1]), Double.parseDouble(f[2]), "settlement",
                        "t".equals(f[4]) ? "town" : "city");
                p.en = f[0];
                p.country = f[3];
                l.add(p);
            } catch (Exception ignore) {
                // skip a malformed line
            }
        }
        cache = l;
        return cache;
    }

    public static int rawCount() {
        return D.length;
    }
}

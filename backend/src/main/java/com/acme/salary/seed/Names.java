package com.acme.salary.seed;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Name pools by country, so the directory reads like a real international company. Plain ASCII, so
 * emails can be built from the names directly.
 */
final class Names {

    private record Pool(List<String> firstNames, List<String> lastNames) {
    }

    private static final Pool ENGLISH = new Pool(
            List.of("James", "Olivia", "Liam", "Emma", "Noah", "Ava", "Oliver", "Sophia", "Ethan", "Isabella",
                    "Lucas", "Mia", "Mason", "Charlotte", "Logan", "Amelia", "Jack", "Harper", "Henry", "Evelyn",
                    "Samuel", "Grace", "Daniel", "Chloe", "Ryan", "Zoe", "Nathan", "Lily", "Owen", "Hannah",
                    "Aaron", "Ruby", "Caleb", "Ella", "Dylan", "Maya"),
            List.of("Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Wilson", "Taylor",
                    "Anderson", "Thomas", "Moore", "Martin", "Jackson", "White", "Harris", "Clark", "Lewis",
                    "Walker", "Hall", "Young", "King", "Wright", "Scott", "Green", "Baker", "Adams", "Nelson",
                    "Carter", "Mitchell", "Roberts", "Turner", "Phillips", "Campbell", "Parker", "Evans"));

    private static final Pool INDIAN = new Pool(
            List.of("Aarav", "Priya", "Vivaan", "Ananya", "Aditya", "Diya", "Arjun", "Isha", "Rohan", "Kavya",
                    "Sai", "Meera", "Karan", "Neha", "Rahul", "Pooja", "Vikram", "Sneha", "Nikhil", "Divya",
                    "Siddharth", "Riya", "Akash", "Shreya", "Manish", "Lakshmi", "Varun", "Aditi", "Harish",
                    "Nandini"),
            List.of("Sharma", "Patel", "Iyer", "Reddy", "Nair", "Gupta", "Menon", "Rao", "Singh", "Kumar",
                    "Joshi", "Mehta", "Pillai", "Desai", "Chopra", "Bose", "Kapoor", "Verma", "Agarwal", "Bhat",
                    "Krishnan", "Mishra", "Banerjee", "Shetty", "Kulkarni", "Naidu", "Saxena", "Pandey"));

    private static final Pool GERMAN = new Pool(
            List.of("Lukas", "Lena", "Felix", "Anna", "Jonas", "Laura", "Leon", "Sophie", "Paul", "Marie",
                    "Maximilian", "Julia", "Tobias", "Hannah", "Niklas", "Lea", "Jan", "Katharina", "Florian",
                    "Johanna", "Moritz", "Clara", "Sebastian", "Emilia", "Tim", "Frieda"),
            List.of("Mueller", "Schmidt", "Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Schulz",
                    "Hoffmann", "Koch", "Richter", "Klein", "Wolf", "Neumann", "Schwarz", "Zimmermann", "Braun",
                    "Krueger", "Hartmann", "Lange", "Werner", "Krause", "Lehmann"));

    private static final Pool FRENCH = new Pool(
            List.of("Louis", "Camille", "Gabriel", "Chloe", "Hugo", "Manon", "Arthur", "Lea", "Jules", "Ines",
                    "Raphael", "Louise", "Adam", "Juliette", "Theo", "Margaux", "Antoine", "Elise", "Mathis",
                    "Clemence", "Baptiste", "Amandine"),
            List.of("Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy",
                    "Moreau", "Simon", "Laurent", "Lefebvre", "Michel", "Garcia", "David", "Bertrand", "Roux",
                    "Vincent", "Fournier", "Morel", "Girard", "Andre", "Mercier"));

    private static final Pool SINGAPOREAN = new Pool(
            List.of("Wei", "Hui Min", "Jun Jie", "Mei Ling", "Zhi Hao", "Xin Yi", "Kai", "Shu Fen", "Ravi",
                    "Siti", "Ahmad", "Nurul", "Daniel", "Rachel", "Marcus", "Grace", "Ethan", "Chloe", "Arun",
                    "Farah"),
            List.of("Tan", "Lim", "Lee", "Ng", "Ong", "Wong", "Goh", "Chua", "Chan", "Koh", "Teo", "Ang", "Yeo",
                    "Tay", "Ho", "Low", "Rahman", "Abdullah", "Pillay", "Nair", "Chong", "Sim"));

    private static final Map<String, Pool> BY_COUNTRY = Map.of(
            "IN", INDIAN,
            "DE", GERMAN,
            "FR", FRENCH,
            "SG", SINGAPOREAN);

    private Names() {
    }

    /** {@code [firstName, lastName]} suited to the country. */
    static String[] pick(String countryCode, Random random) {
        Pool pool = BY_COUNTRY.getOrDefault(countryCode, ENGLISH);
        return new String[] {
                pool.firstNames().get(random.nextInt(pool.firstNames().size())),
                pool.lastNames().get(random.nextInt(pool.lastNames().size()))};
    }
}

"""
Generates the deterministic seed catalogue used by local development, tests and
the offline verification harness.

Everything here is factual film metadata (title/year/runtime/genre/crew) plus
SYNTHETIC user activity. No real personal data is used: every demo account is
an obviously fictional persona on the reserved `example.com` domain.

Output: backend/src/main/resources/db/seed/R__seed_catalogue.sql
        backend/src/main/resources/db/seed/R__seed_demo_activity.sql

`R__` (repeatable) prefixes are used so Flyway re-applies them when the content
changes, and so they can be excluded from production via flyway.locations.
"""
from __future__ import annotations

import pathlib
import random
import re
import unicodedata

OUT = pathlib.Path("backend/src/main/resources/db/seed")
random.seed(20240817)  # deterministic output


def q(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def slugify(text: str, year: str) -> str:
    norm = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    s = re.sub(r"[^a-z0-9]+", "-", norm.lower()).strip("-")
    return f"{s}-{year}"


GENRES = [
    (28, "Action"), (12, "Adventure"), (16, "Animation"), (35, "Comedy"),
    (80, "Crime"), (99, "Documentary"), (18, "Drama"), (10751, "Family"),
    (14, "Fantasy"), (36, "History"), (27, "Horror"), (10402, "Music"),
    (9648, "Mystery"), (10749, "Romance"), (878, "Science Fiction"),
    (53, "Thriller"), (10752, "War"), (37, "Western"),
]

# title, year, runtime, lang, country, genres, director, writers, cast,
# keywords, ext_rating, votes, popularity, overview, trailer(YouTube id)
FILMS = [
    ("Interstellar", "2014-11-07", 169, "en", "United States",
     ["Science Fiction", "Drama", "Adventure"], "Christopher Nolan",
     ["Jonathan Nolan", "Christopher Nolan"],
     ["Matthew McConaughey", "Anne Hathaway", "Jessica Chastain", "Michael Caine"],
     ["space", "wormhole", "time dilation", "father daughter relationship", "survival"],
     8.4, 34000, 92.5,
     "When Earth becomes uninhabitable, a former test pilot leads an expedition through a newly discovered wormhole to find humanity a new home.",
     "zSWdZVtXT7E"),
    ("Inception", "2010-07-16", 148, "en", "United States",
     ["Action", "Science Fiction", "Thriller"], "Christopher Nolan",
     ["Christopher Nolan"],
     ["Leonardo DiCaprio", "Joseph Gordon-Levitt", "Elliot Page", "Tom Hardy"],
     ["dream", "heist", "subconscious", "mind bending", "corporate espionage"],
     8.4, 36000, 95.1,
     "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea into a target's mind.",
     "YoHD9XEInc0"),
    ("The Dark Knight", "2008-07-18", 152, "en", "United States",
     ["Action", "Crime", "Drama", "Thriller"], "Christopher Nolan",
     ["Jonathan Nolan", "Christopher Nolan"],
     ["Christian Bale", "Heath Ledger", "Aaron Eckhart", "Michael Caine"],
     ["vigilante", "anarchy", "moral dilemma", "organised crime", "superhero"],
     8.5, 32000, 97.3,
     "Batman faces the Joker, an anarchic criminal mastermind determined to prove that even the most principled citizens can be broken.",
     "EXeTwQWrcwY"),
    ("Arrival", "2016-11-11", 116, "en", "United States",
     ["Science Fiction", "Drama", "Mystery"], "Denis Villeneuve",
     ["Eric Heisserer"],
     ["Amy Adams", "Jeremy Renner", "Forest Whitaker"],
     ["first contact", "linguistics", "non-linear time", "aliens", "grief"],
     7.6, 19000, 71.4,
     "A linguist is recruited to communicate with extraterrestrial visitors and discovers their language reshapes how she perceives time.",
     "tFMo3UJ4B4g"),
    ("Blade Runner 2049", "2017-10-06", 164, "en", "United States",
     ["Science Fiction", "Drama", "Mystery"], "Denis Villeneuve",
     ["Hampton Fancher", "Michael Green"],
     ["Ryan Gosling", "Harrison Ford", "Ana de Armas", "Jared Leto"],
     ["replicant", "dystopia", "identity", "neo-noir", "artificial intelligence"],
     7.6, 14000, 68.2,
     "A young blade runner uncovers a long-buried secret capable of plunging what remains of society into chaos.",
     "gCcx85zbxz4"),
    ("Dune", "2021-09-15", 155, "en", "United States",
     ["Science Fiction", "Adventure", "Drama"], "Denis Villeneuve",
     ["Jon Spaihts", "Denis Villeneuve", "Eric Roth"],
     ["Timothée Chalamet", "Rebecca Ferguson", "Oscar Isaac", "Zendaya"],
     ["desert planet", "prophecy", "political intrigue", "spice", "coming of age"],
     7.8, 12000, 88.9,
     "Paul Atreides travels to the most dangerous planet in the universe to secure the future of his family and his people.",
     "n9xhJrPXop4"),
    ("Parasite", "2019-05-30", 132, "ko", "South Korea",
     ["Thriller", "Drama", "Comedy"], "Bong Joon-ho",
     ["Bong Joon-ho", "Han Jin-won"],
     ["Song Kang-ho", "Lee Sun-kyun", "Cho Yeo-jeong", "Choi Woo-shik"],
     ["class conflict", "social satire", "con artists", "dark comedy", "inequality"],
     8.5, 17000, 84.7,
     "A destitute family schemes their way into the household of a wealthy clan, with consequences none of them anticipate.",
     "5xH0HfJHsaY"),
    ("Spirited Away", "2001-07-20", 125, "ja", "Japan",
     ["Animation", "Fantasy", "Family", "Adventure"], "Hayao Miyazaki",
     ["Hayao Miyazaki"],
     ["Rumi Hiiragi", "Miyu Irino", "Mari Natsuki"],
     ["spirit world", "coming of age", "hand drawn animation", "folklore", "courage"],
     8.5, 16000, 79.6,
     "A sullen ten-year-old is trapped in a world of spirits and must find the resolve to free her parents and herself.",
     "ByXuk9QqQkk"),
    ("Your Name.", "2016-08-26", 106, "ja", "Japan",
     ["Animation", "Romance", "Fantasy", "Drama"], "Makoto Shinkai",
     ["Makoto Shinkai"],
     ["Ryunosuke Kamiki", "Mone Kamishiraishi"],
     ["body swap", "comet", "star crossed", "rural japan", "memory"],
     8.5, 11000, 74.1,
     "Two teenagers discover they are inexplicably swapping bodies, and their attempts to meet uncover a looming catastrophe.",
     "xU47nhruN-Q"),
    ("Mad Max: Fury Road", "2015-05-13", 120, "en", "Australia",
     ["Action", "Adventure", "Science Fiction"], "George Miller",
     ["George Miller", "Brendan McCarthy", "Nick Lathouris"],
     ["Tom Hardy", "Charlize Theron", "Nicholas Hoult"],
     ["post apocalyptic", "car chase", "desert", "practical effects", "rebellion"],
     7.6, 22000, 81.3,
     "In a scorched wasteland, a drifter and a renegade commander flee a tyrant across a relentless stretch of desert highway.",
     "hEJnMQG9ev8"),
    ("The Grand Budapest Hotel", "2014-03-07", 99, "en", "Germany",
     ["Comedy", "Drama", "Adventure"], "Wes Anderson",
     ["Wes Anderson"],
     ["Ralph Fiennes", "Tony Revolori", "Adrien Brody", "Saoirse Ronan"],
     ["symmetry", "caper", "interwar europe", "deadpan", "friendship"],
     8.1, 15000, 62.8,
     "A legendary concierge and his trusted lobby boy become embroiled in the theft of a priceless painting.",
     "1Fg5iWmQjwk"),
    ("Whiplash", "2014-10-10", 106, "en", "United States",
     ["Drama", "Music"], "Damien Chazelle", ["Damien Chazelle"],
     ["Miles Teller", "J.K. Simmons", "Melissa Benoist"],
     ["jazz", "obsession", "mentor", "perfectionism", "drumming"],
     8.4, 14000, 66.5,
     "An ambitious young drummer enrols at a cut-throat conservatory where an abusive instructor pushes him past breaking point.",
     "7d_jQycdQGo"),
    ("La La Land", "2016-12-09", 128, "en", "United States",
     ["Romance", "Drama", "Music", "Comedy"], "Damien Chazelle", ["Damien Chazelle"],
     ["Ryan Gosling", "Emma Stone", "John Legend"],
     ["musical", "los angeles", "ambition", "bittersweet", "jazz"],
     7.9, 16000, 70.2,
     "A jazz pianist and an aspiring actress fall in love while chasing ambitions that pull them steadily apart.",
     "0pdqf4P9MB8"),
    ("Get Out", "2017-02-24", 104, "en", "United States",
     ["Horror", "Thriller", "Mystery"], "Jordan Peele", ["Jordan Peele"],
     ["Daniel Kaluuya", "Allison Williams", "Bradley Whitford"],
     ["social thriller", "racism", "hypnosis", "suburbia", "twist"],
     7.6, 14000, 64.9,
     "A young photographer visits his girlfriend's family estate and uncovers a horrifying truth beneath their hospitality.",
     "DzfpyUB60YY"),
    ("The Social Network", "2010-10-01", 120, "en", "United States",
     ["Drama", "History"], "David Fincher", ["Aaron Sorkin"],
     ["Jesse Eisenberg", "Andrew Garfield", "Armie Hammer", "Justin Timberlake"],
     ["startup", "betrayal", "lawsuit", "ambition", "technology"],
     7.4, 11000, 55.1,
     "The founding of a social network becomes a story of friendship, betrayal and litigation.",
     "lB95KLmpLR4"),
    ("Se7en", "1995-09-22", 127, "en", "United States",
     ["Crime", "Thriller", "Mystery", "Drama"], "David Fincher", ["Andrew Kevin Walker"],
     ["Brad Pitt", "Morgan Freeman", "Gwyneth Paltrow", "Kevin Spacey"],
     ["serial killer", "detective", "rain", "neo-noir", "moral decay"],
     8.4, 19000, 63.7,
     "Two detectives hunt a killer who stages his murders around the seven deadly sins.",
     "znmZoVkCjpI"),
    ("Fight Club", "1999-10-15", 139, "en", "United States",
     ["Drama", "Thriller"], "David Fincher", ["Jim Uhls"],
     ["Brad Pitt", "Edward Norton", "Helena Bonham Carter"],
     ["insomnia", "consumerism", "split personality", "anarchy", "twist"],
     8.4, 28000, 72.4,
     "An insomniac office worker and a soap salesman form an underground club that spirals far beyond their control.",
     "qtRKdVHc-cE"),
    ("Pulp Fiction", "1994-09-10", 154, "en", "United States",
     ["Crime", "Thriller", "Drama"], "Quentin Tarantino",
     ["Quentin Tarantino", "Roger Avary"],
     ["John Travolta", "Samuel L. Jackson", "Uma Thurman", "Bruce Willis"],
     ["nonlinear narrative", "hitman", "dialogue driven", "crime", "dark comedy"],
     8.5, 26000, 76.8,
     "The lives of two hitmen, a boxer and a gangster's wife intertwine across four tales of violence and redemption.",
     "s7EdQ4FqbhY"),
    ("The Matrix", "1999-03-31", 136, "en", "United States",
     ["Action", "Science Fiction"], "Lana Wachowski",
     ["Lana Wachowski", "Lilly Wachowski"],
     ["Keanu Reeves", "Laurence Fishburne", "Carrie-Anne Moss", "Hugo Weaving"],
     ["simulation", "chosen one", "cyberpunk", "artificial intelligence", "martial arts"],
     8.2, 24000, 87.2,
     "A hacker discovers reality is a simulation and joins a rebellion against the machines that built it.",
     "vKQi3bBA1y8"),
    ("Goodfellas", "1990-09-12", 145, "en", "United States",
     ["Crime", "Drama"], "Martin Scorsese", ["Nicholas Pileggi", "Martin Scorsese"],
     ["Robert De Niro", "Ray Liotta", "Joe Pesci", "Lorraine Bracco"],
     ["mafia", "rise and fall", "voiceover", "organised crime", "loyalty"],
     8.5, 12000, 58.9,
     "Three decades of life inside the mob, from petty errands to paranoia and betrayal.",
     "2ilzidi_J8Q"),
    ("The Departed", "2006-10-06", 151, "en", "United States",
     ["Crime", "Thriller", "Drama"], "Martin Scorsese", ["William Monahan"],
     ["Leonardo DiCaprio", "Matt Damon", "Jack Nicholson", "Mark Wahlberg"],
     ["undercover", "mole", "boston", "double life", "organised crime"],
     8.2, 13000, 61.2,
     "An undercover officer and a mole inside the police race to expose one another before either is discovered.",
     "auYbpnEwBBg"),
    ("Oppenheimer", "2023-07-21", 181, "en", "United States",
     ["Drama", "History", "Thriller"], "Christopher Nolan", ["Christopher Nolan"],
     ["Cillian Murphy", "Emily Blunt", "Robert Downey Jr.", "Matt Damon"],
     ["manhattan project", "physics", "moral dilemma", "biography", "cold war"],
     8.1, 9000, 94.6,
     "The theoretical physicist who led the Manhattan Project confronts the consequences of the weapon he created.",
     "uYPbbksJxIg"),
    ("Everything Everywhere All at Once", "2022-03-25", 139, "en", "United States",
     ["Science Fiction", "Comedy", "Adventure", "Drama"], "Daniel Kwan",
     ["Daniel Kwan", "Daniel Scheinert"],
     ["Michelle Yeoh", "Ke Huy Quan", "Stephanie Hsu", "Jamie Lee Curtis"],
     ["multiverse", "family", "absurdist", "generational trauma", "kung fu"],
     7.8, 8000, 77.3,
     "A laundromat owner is swept into a multiverse-spanning crisis that only she can resolve.",
     "wxN1T1uxQ2g"),
    ("Spider-Man: Into the Spider-Verse", "2018-12-14", 117, "en", "United States",
     ["Animation", "Action", "Adventure", "Science Fiction"], "Bob Persichetti",
     ["Phil Lord", "Rodney Rothman"],
     ["Shameik Moore", "Jake Johnson", "Hailee Steinfeld", "Mahershala Ali"],
     ["multiverse", "comic book", "coming of age", "stylised animation", "mentor"],
     8.4, 13000, 83.5,
     "A Brooklyn teenager becomes Spider-Man and meets counterparts pulled from across the multiverse.",
     "g4Hbz2jLxvQ"),
    ("Coco", "2017-10-27", 105, "en", "United States",
     ["Animation", "Family", "Fantasy", "Music"], "Lee Unkrich",
     ["Adrian Molina", "Matthew Aldrich"],
     ["Anthony Gonzalez", "Gael García Bernal", "Benjamin Bratt"],
     ["day of the dead", "family", "music", "afterlife", "memory"],
     8.2, 17000, 69.8,
     "A boy who dreams of music journeys to the Land of the Dead to uncover his family's hidden history.",
     "Ga6RYejo6Hk"),
    ("Knives Out", "2019-11-27", 130, "en", "United States",
     ["Mystery", "Comedy", "Crime", "Drama"], "Rian Johnson", ["Rian Johnson"],
     ["Daniel Craig", "Ana de Armas", "Chris Evans", "Jamie Lee Curtis"],
     ["whodunit", "inheritance", "detective", "ensemble", "twist"],
     7.9, 13000, 67.4,
     "A private detective investigates the death of a wealthy novelist among a family of suspects.",
     "qGqiHJTsRkQ"),
    ("The Prestige", "2006-10-20", 130, "en", "United States",
     ["Drama", "Mystery", "Science Fiction", "Thriller"], "Christopher Nolan",
     ["Jonathan Nolan", "Christopher Nolan"],
     ["Christian Bale", "Hugh Jackman", "Scarlett Johansson", "Michael Caine"],
     ["magic", "rivalry", "obsession", "twist", "victorian"],
     8.2, 14000, 59.6,
     "Two rival magicians in Victorian London push their obsession with one another to devastating extremes.",
     "o4gHCmTQDVI"),
    ("Gone Girl", "2014-10-01", 149, "en", "United States",
     ["Thriller", "Mystery", "Drama", "Crime"], "David Fincher", ["Gillian Flynn"],
     ["Ben Affleck", "Rosamund Pike", "Neil Patrick Harris"],
     ["missing person", "unreliable narrator", "media circus", "marriage", "twist"],
     7.9, 13000, 60.3,
     "When a woman vanishes on her anniversary, her husband becomes the prime suspect in a national media storm.",
     "2-_-1nJf8Vg"),
    ("Sicario", "2015-09-18", 121, "en", "United States",
     ["Action", "Crime", "Thriller", "Drama"], "Denis Villeneuve", ["Taylor Sheridan"],
     ["Emily Blunt", "Benicio del Toro", "Josh Brolin"],
     ["cartel", "border", "moral ambiguity", "task force", "tension"],
     7.4, 9000, 52.7,
     "An idealistic FBI agent is enlisted into a shadowy task force operating on the US-Mexico border.",
     "G8tlEcnrGnM"),
    ("Prisoners", "2013-09-20", 153, "en", "United States",
     ["Thriller", "Drama", "Crime", "Mystery"], "Denis Villeneuve", ["Aaron Guzikowski"],
     ["Hugh Jackman", "Jake Gyllenhaal", "Viola Davis", "Paul Dano"],
     ["abduction", "vigilante", "desperation", "investigation", "moral dilemma"],
     8.1, 11000, 57.8,
     "When his daughter disappears, a father takes the investigation into his own hands as a detective closes in.",
     "bpXfcTF6iVk"),
    ("1917", "2019-12-25", 119, "en", "United Kingdom",
     ["War", "Drama", "Action", "History"], "Sam Mendes",
     ["Sam Mendes", "Krysty Wilson-Cairns"],
     ["George MacKay", "Dean-Charles Chapman", "Mark Strong"],
     ["world war i", "one shot", "mission", "trenches", "brotherhood"],
     7.9, 11000, 63.1,
     "Two British soldiers race across enemy territory to deliver a message that could save 1,600 men.",
     "YqNYrYUiMfg"),
    ("Dunkirk", "2017-07-21", 106, "en", "United Kingdom",
     ["War", "Action", "Drama", "History"], "Christopher Nolan", ["Christopher Nolan"],
     ["Fionn Whitehead", "Tom Hardy", "Mark Rylance", "Cillian Murphy"],
     ["world war ii", "evacuation", "survival", "non-linear", "tension"],
     7.5, 12000, 58.4,
     "Allied soldiers are surrounded on the beaches of Dunkirk as land, sea and air rescue efforts converge.",
     "F-eMt3SrfFU"),
    ("Portrait of a Lady on Fire", "2019-09-18", 122, "fr", "France",
     ["Romance", "Drama", "History"], "Céline Sciamma", ["Céline Sciamma"],
     ["Noémie Merlant", "Adèle Haenel", "Luàna Bajrami"],
     ["painting", "forbidden love", "18th century", "gaze", "memory"],
     8.1, 4000, 44.2,
     "On a remote island, a painter commissioned to portray a reluctant bride finds the sittings becoming something else.",
     "R-fQPTwma9o"),
    ("Amélie", "2001-04-25", 122, "fr", "France",
     ["Comedy", "Romance"], "Jean-Pierre Jeunet",
     ["Guillaume Laurant", "Jean-Pierre Jeunet"],
     ["Audrey Tautou", "Mathieu Kassovitz"],
     ["paris", "whimsy", "kindness", "montmartre", "quirky"],
     7.9, 10000, 51.9,
     "A shy Parisian waitress decides to orchestrate small acts of kindness in the lives of those around her.",
     "HUECWi5pX7o"),
    ("Pan's Labyrinth", "2006-10-11", 118, "es", "Mexico",
     ["Fantasy", "Drama", "War"], "Guillermo del Toro", ["Guillermo del Toro"],
     ["Ivana Baquero", "Sergi López", "Maribel Verdú"],
     ["fascist spain", "dark fairy tale", "faun", "escapism", "childhood"],
     7.8, 8000, 49.6,
     "In post-civil-war Spain, a young girl escapes into a labyrinth of dangerous fairy-tale bargains.",
     "EqYiSlkvRuw"),
    ("City of God", "2002-08-30", 130, "pt", "Brazil",
     ["Crime", "Drama"], "Fernando Meirelles", ["Bráulio Mantovani"],
     ["Alexandre Rodrigues", "Leandro Firmino", "Matheus Nachtergaele"],
     ["favela", "gang", "photography", "coming of age", "violence"],
     8.4, 8000, 47.3,
     "Two boys growing up in a Rio favela take divergent paths: one becomes a photographer, the other a kingpin.",
     "ioUE_5wpg_E"),
    ("Oldboy", "2003-11-21", 120, "ko", "South Korea",
     ["Thriller", "Mystery", "Action", "Drama"], "Park Chan-wook",
     ["Park Chan-wook", "Hwang Jo-yun"],
     ["Choi Min-sik", "Yoo Ji-tae", "Kang Hye-jung"],
     ["revenge", "imprisonment", "twist", "hallway fight", "obsession"],
     8.3, 7000, 46.8,
     "A man imprisoned for fifteen years without explanation is released and given five days to find his captor.",
     "2HkjrJ6IK5E"),
    ("Memories of Murder", "2003-05-02", 132, "ko", "South Korea",
     ["Crime", "Drama", "Mystery", "Thriller"], "Bong Joon-ho",
     ["Bong Joon-ho", "Shim Sung-bo"],
     ["Song Kang-ho", "Kim Sang-kyung", "Kim Roi-ha"],
     ["serial killer", "investigation", "1980s", "rural", "unsolved"],
     8.1, 4000, 42.5,
     "Detectives in rural Korea struggle to solve the country's first recorded serial murders.",
     "Yz2wYWkFrKM"),
    ("Princess Mononoke", "1997-07-12", 134, "ja", "Japan",
     ["Animation", "Fantasy", "Adventure"], "Hayao Miyazaki", ["Hayao Miyazaki"],
     ["Yoji Matsuda", "Yuriko Ishida", "Yuko Tanaka"],
     ["nature versus industry", "forest spirits", "curse", "folklore", "war"],
     8.3, 8000, 55.4,
     "A cursed prince is drawn into the war between an iron-working settlement and the gods of the forest.",
     "4OiMOHRDs14"),
    ("Grave of the Fireflies", "1988-04-16", 89, "ja", "Japan",
     ["Animation", "Drama", "War"], "Isao Takahata", ["Isao Takahata"],
     ["Tsutomu Tatsumi", "Ayano Shiraishi"],
     ["world war ii", "siblings", "famine", "tragedy", "childhood"],
     8.5, 5000, 40.1,
     "A teenage boy and his little sister struggle to survive in Japan during the final months of the war.",
     "4vPeTSRd580"),
    ("The Shawshank Redemption", "1994-09-23", 142, "en", "United States",
     ["Drama", "Crime"], "Frank Darabont", ["Frank Darabont"],
     ["Tim Robbins", "Morgan Freeman", "Bob Gunton"],
     ["prison", "friendship", "hope", "wrongful conviction", "escape"],
     8.7, 27000, 89.4,
     "A banker sentenced for a crime he did not commit forms an enduring friendship over two decades inside.",
     "6hB3S9bIaco"),
    ("Forrest Gump", "1994-07-06", 142, "en", "United States",
     ["Drama", "Romance", "Comedy"], "Robert Zemeckis", ["Eric Roth"],
     ["Tom Hanks", "Robin Wright", "Gary Sinise"],
     ["americana", "innocence", "history", "love", "destiny"],
     8.5, 26000, 78.6,
     "An extraordinary life unfolds as a kind-hearted man from Alabama drifts through decades of American history.",
     "bLvqoHBptjg"),
    ("The Lord of the Rings: The Fellowship of the Ring", "2001-12-19", 179, "en", "New Zealand",
     ["Fantasy", "Adventure", "Action"], "Peter Jackson",
     ["Fran Walsh", "Philippa Boyens", "Peter Jackson"],
     ["Elijah Wood", "Ian McKellen", "Viggo Mortensen", "Sean Bean"],
     ["quest", "fellowship", "middle earth", "ring", "epic"],
     8.4, 24000, 85.7,
     "A hobbit inherits a ring of terrible power and sets out with eight companions to destroy it.",
     "V75dMMIW2B4"),
    ("Alien", "1979-05-25", 117, "en", "United Kingdom",
     ["Horror", "Science Fiction", "Thriller"], "Ridley Scott", ["Dan O'Bannon"],
     ["Sigourney Weaver", "Tom Skerritt", "John Hurt", "Ian Holm"],
     ["space horror", "xenomorph", "isolation", "survival", "corporate greed"],
     8.2, 14000, 61.9,
     "The crew of a commercial towing ship answers a distress signal and brings something lethal aboard.",
     "LjLamj-b0I8"),
    ("The Thing", "1982-06-25", 109, "en", "United States",
     ["Horror", "Science Fiction", "Mystery"], "John Carpenter", ["Bill Lancaster"],
     ["Kurt Russell", "Wilford Brimley", "Keith David"],
     ["paranoia", "antarctica", "shapeshifter", "practical effects", "isolation"],
     8.2, 8000, 50.2,
     "Researchers in Antarctica confront a parasitic organism that perfectly imitates its victims.",
     "5ftmr17M-a4"),
    ("Heat", "1995-12-15", 170, "en", "United States",
     ["Crime", "Drama", "Thriller", "Action"], "Michael Mann", ["Michael Mann"],
     ["Al Pacino", "Robert De Niro", "Val Kilmer"],
     ["heist", "cat and mouse", "los angeles", "professionalism", "shootout"],
     7.9, 8000, 48.7,
     "A career thief and the detective pursuing him find they have more in common than either expects.",
     "8Nu5IdT4hAA"),
    ("No Country for Old Men", "2007-11-08", 122, "en", "United States",
     ["Crime", "Drama", "Thriller"], "Joel Coen", ["Joel Coen", "Ethan Coen"],
     ["Tommy Lee Jones", "Javier Bardem", "Josh Brolin"],
     ["west texas", "hitman", "fate", "chase", "moral decay"],
     8.0, 11000, 54.3,
     "A hunter stumbles on drug money in the Texas desert and is pursued by an implacable killer.",
     "38A__WT3-o0"),
    ("Children of Men", "2006-09-22", 109, "en", "United Kingdom",
     ["Science Fiction", "Drama", "Thriller", "Action"], "Alfonso Cuarón",
     ["Alfonso Cuarón", "Timothy J. Sexton"],
     ["Clive Owen", "Julianne Moore", "Michael Caine"],
     ["infertility", "dystopia", "long take", "refugees", "hope"],
     7.6, 7000, 45.8,
     "In a world where humanity has become infertile, a disillusioned bureaucrat escorts the only pregnant woman alive.",
     "2VT2apoX90o"),
    ("Gravity", "2013-10-04", 91, "en", "United States",
     ["Science Fiction", "Thriller", "Drama"], "Alfonso Cuarón",
     ["Alfonso Cuarón", "Jonás Cuarón"],
     ["Sandra Bullock", "George Clooney"],
     ["space", "survival", "isolation", "debris", "rebirth"],
     7.2, 13000, 56.7,
     "Two astronauts are left adrift after debris destroys their shuttle during a routine spacewalk.",
     "OiTiKOy59o4"),
    ("Ex Machina", "2015-01-21", 108, "en", "United Kingdom",
     ["Science Fiction", "Drama", "Thriller"], "Alex Garland", ["Alex Garland"],
     ["Domhnall Gleeson", "Alicia Vikander", "Oscar Isaac"],
     ["artificial intelligence", "turing test", "manipulation", "isolation", "consciousness"],
     7.7, 12000, 59.1,
     "A programmer is invited to administer the Turing test to an unnervingly convincing android.",
     "XYGzRB4Pnq8"),
    ("Nope", "2022-07-22", 130, "en", "United States",
     ["Horror", "Science Fiction", "Mystery", "Thriller"], "Jordan Peele", ["Jordan Peele"],
     ["Daniel Kaluuya", "Keke Palmer", "Steven Yeun"],
     ["ufo", "spectacle", "ranch", "siblings", "sky"],
     6.9, 5000, 53.6,
     "Horse-ranching siblings in California attempt to capture evidence of an impossible presence in the sky.",
     "in8sBgSyaUw"),
    ("The Wild Robot", "2024-09-12", 102, "en", "United States",
     ["Animation", "Family", "Science Fiction", "Adventure"], "Chris Sanders", ["Chris Sanders"],
     ["Lupita Nyong'o", "Pedro Pascal", "Catherine O'Hara"],
     ["robot", "nature", "parenthood", "survival", "wilderness"],
     8.2, 4000, 91.2,
     "A shipwrecked service robot learns to survive on an uninhabited island and raises an orphaned gosling.",
     "67tHtpac5ws"),
    ("Poor Things", "2023-12-08", 141, "en", "United Kingdom",
     ["Science Fiction", "Comedy", "Drama", "Romance"], "Yorgos Lanthimos", ["Tony McNamara"],
     ["Emma Stone", "Mark Ruffalo", "Willem Dafoe"],
     ["reanimation", "victorian", "self discovery", "absurdist", "surreal"],
     7.8, 5000, 73.9,
     "A young woman brought back to life by an unorthodox scientist embarks on an odyssey of self-discovery.",
     "RlbR5N6veqw"),
    ("Past Lives", "2023-06-02", 105, "en", "United States",
     ["Romance", "Drama"], "Celine Song", ["Celine Song"],
     ["Greta Lee", "Teo Yoo", "John Magaro"],
     ["destiny", "immigration", "reunion", "longing", "quiet"],
     7.8, 3000, 48.4,
     "Two childhood friends reunite in New York two decades after one of them emigrated from Korea.",
     "kA244xewjcI"),
    ("The Zone of Interest", "2023-12-15", 105, "de", "United Kingdom",
     ["Drama", "History", "War"], "Jonathan Glazer", ["Jonathan Glazer"],
     ["Christian Friedel", "Sandra Hüller"],
     ["holocaust", "banality of evil", "sound design", "domesticity", "history"],
     7.4, 2000, 39.7,
     "A commandant and his wife build an idyllic domestic life directly alongside Auschwitz.",
     "SUZaWuID7Qk"),
    ("Godzilla Minus One", "2023-11-03", 125, "ja", "Japan",
     ["Science Fiction", "Action", "Drama", "Horror"], "Takashi Yamazaki", ["Takashi Yamazaki"],
     ["Ryunosuke Kamiki", "Minami Hamabe"],
     ["kaiju", "postwar japan", "survivor guilt", "rebuilding", "sacrifice"],
     7.8, 3000, 66.3,
     "A traumatised former pilot confronts a monstrous force threatening a Japan still rebuilding from war.",
     "SrCzOxfWnyM"),
    ("Perfect Days", "2023-12-21", 124, "ja", "Japan",
     ["Drama"], "Wim Wenders", ["Wim Wenders", "Takuma Takasaki"],
     ["Koji Yakusho", "Tokio Emoto"],
     ["routine", "tokyo", "contentment", "solitude", "quiet"],
     7.9, 1500, 37.2,
     "A Tokyo toilet cleaner finds profound contentment in the rhythms of an unremarkable daily routine.",
     "hUeMKW2X6bs"),
    ("Anatomy of a Fall", "2023-08-23", 151, "fr", "France",
     ["Drama", "Mystery", "Crime", "Thriller"], "Justine Triet",
     ["Justine Triet", "Arthur Harari"],
     ["Sandra Hüller", "Swann Arlaud", "Milo Machado-Graner"],
     ["courtroom", "marriage", "ambiguity", "investigation", "truth"],
     7.7, 2500, 44.9,
     "A novelist stands trial for her husband's death, with their partially sighted son the only witness.",
     "MWkhkGvUJ_0"),
    ("Drive", "2011-09-16", 100, "en", "United States",
     ["Crime", "Drama", "Thriller"], "Nicolas Winding Refn", ["Hossein Amini"],
     ["Ryan Gosling", "Carey Mulligan", "Bryan Cranston", "Oscar Isaac"],
     ["getaway driver", "neo-noir", "synthwave", "los angeles", "violence"],
     7.6, 12000, 57.2,
     "A Hollywood stunt driver moonlighting as a getaway wheelman is drawn into a heist gone wrong.",
     "KBiOF3y1W0Y"),
    ("Moonlight", "2016-10-21", 111, "en", "United States",
     ["Drama"], "Barry Jenkins", ["Barry Jenkins"],
     ["Trevante Rhodes", "Mahershala Ali", "Naomie Harris"],
     ["coming of age", "identity", "miami", "three acts", "tenderness"],
     7.4, 7000, 43.6,
     "Three chapters in the life of a young man growing up in Miami as he grapples with identity and belonging.",
     "9NJj12tJzqc"),
    ("Roma", "2018-08-30", 135, "es", "Mexico",
     ["Drama"], "Alfonso Cuarón", ["Alfonso Cuarón"],
     ["Yalitza Aparicio", "Marina de Tavira"],
     ["domestic worker", "1970s", "black and white", "memory", "family"],
     7.2, 5000, 41.8,
     "A year in the life of a live-in housekeeper for a middle-class family in 1970s Mexico City.",
     "6BS27ngZtxg"),
]


def build_catalogue() -> str:
    lines: list[str] = [
        "-- Generated by tools/offline-verify/generate_seed.py - do not edit by hand.",
        "-- Factual film metadata for local development and tests.",
        "",
        "-- Genres --------------------------------------------------------------",
    ]
    for tmdb_id, name in GENRES:
        slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        lines.append(
            f"INSERT INTO genres (tmdb_id, name, slug) VALUES ({tmdb_id}, {q(name)}, {q(slug)}) "
            "ON CONFLICT (name) DO NOTHING;")

    people: dict[str, None] = {}
    keywords: dict[str, None] = {}
    for f in FILMS:
        people[f[6]] = None
        for w in f[7]:
            people[w] = None
        for c in f[8]:
            people[c] = None
        for k in f[9]:
            keywords[k] = None

    lines += ["", "-- People --------------------------------------------------------------"]
    for name in people:
        lines.append(f"INSERT INTO people (name) VALUES ({q(name)}) ON CONFLICT DO NOTHING;")

    lines += ["", "-- Keywords ------------------------------------------------------------"]
    for kw in keywords:
        lines.append(f"INSERT INTO keywords (name) VALUES ({q(kw)}) ON CONFLICT (name) DO NOTHING;")

    lines += ["", "-- Movies --------------------------------------------------------------"]
    for idx, f in enumerate(FILMS, start=1):
        (title, release, runtime, lang, country, genres, director, writers,
         cast, kws, rating, votes, popularity, overview, trailer) = f
        year = release[:4]
        slug = slugify(title, year)
        poster = f"https://image.tmdb.org/t/p/w500/seed/{slug}.jpg"
        backdrop = f"https://image.tmdb.org/t/p/w1280/seed/{slug}-backdrop.jpg"
        lines.append(
            "INSERT INTO movies (title, original_title, slug, overview, release_date, "
            "runtime_minutes, original_language, origin_country, status, poster_url, "
            "backdrop_url, trailer_url, external_rating, external_vote_count, popularity, adult) "
            f"VALUES ({q(title)}, {q(title)}, {q(slug)}, {q(overview)}, {q(release)}, "
            f"{runtime}, {q(lang)}, {q(country)}, 'RELEASED', {q(poster)}, {q(backdrop)}, "
            f"{q('https://www.youtube.com/watch?v=' + trailer)}, {rating}, {votes}, "
            f"{popularity}, FALSE) ON CONFLICT (slug) DO NOTHING;")

        for g in genres:
            lines.append(
                "INSERT INTO movie_genres (movie_id, genre_id) SELECT m.id, g.id FROM movies m, genres g "
                f"WHERE m.slug = {q(slug)} AND g.name = {q(g)} ON CONFLICT DO NOTHING;")
        for kw in kws:
            lines.append(
                "INSERT INTO movie_keywords (movie_id, keyword_id) SELECT m.id, k.id FROM movies m, keywords k "
                f"WHERE m.slug = {q(slug)} AND k.name = {q(kw)} ON CONFLICT DO NOTHING;")
        lines.append(
            "INSERT INTO movie_credits (movie_id, person_id, credit_type, job, display_order) "
            "SELECT m.id, p.id, 'DIRECTOR', 'Director', 0 FROM movies m, people p "
            f"WHERE m.slug = {q(slug)} AND p.name = {q(director)} ON CONFLICT DO NOTHING;")
        for order, w in enumerate(writers):
            lines.append(
                "INSERT INTO movie_credits (movie_id, person_id, credit_type, job, display_order) "
                f"SELECT m.id, p.id, 'WRITER', 'Screenplay', {order} FROM movies m, people p "
                f"WHERE m.slug = {q(slug)} AND p.name = {q(w)} ON CONFLICT DO NOTHING;")
        for order, c in enumerate(cast):
            lines.append(
                "INSERT INTO movie_credits (movie_id, person_id, credit_type, character_name, display_order) "
                f"SELECT m.id, p.id, 'CAST', NULL, {order} FROM movies m, people p "
                f"WHERE m.slug = {q(slug)} AND p.name = {q(c)} ON CONFLICT DO NOTHING;")
    return "\n".join(lines) + "\n"


# --- Synthetic demo users ---------------------------------------------------
# Fictional personas on the reserved example.com domain. The password hash is a
# real BCrypt digest of the documented development password.
DEMO_PASSWORD_HASH = "$2a$10$Dow1Y7Qh8vQxYQ4eK1oO8u7pQnR0hJZ8m9nQ0sT7cV2wX3yZ4aB6C"

PERSONAS = [
    ("admin@example.com", "Ada Administrator", ["ROLE_ADMIN", "ROLE_USER"],
     ["Science Fiction", "Drama"], "sci-fi leaning admin"),
    ("nolan.fan@example.com", "Nina Cortez", ["ROLE_USER"],
     ["Science Fiction", "Thriller", "Drama"], "loves Nolan/Villeneuve"),
    ("animation.buff@example.com", "Kenji Warner", ["ROLE_USER"],
     ["Animation", "Family", "Fantasy"], "animation devotee"),
    ("crime.watcher@example.com", "Marisol Reyes", ["ROLE_USER"],
     ["Crime", "Thriller", "Mystery"], "crime and noir"),
    ("newcomer@example.com", "Sam Okafor", ["ROLE_USER"],
     ["Comedy", "Romance"], "cold-start user: preferences only, no ratings"),
    ("blank.slate@example.com", "Priya Raman", ["ROLE_USER"],
     [], "cold-start user: no signals at all"),
]

# (email, [(movie_title, score)]) - shapes the collaborative signal.
ACTIVITY = {
    "nolan.fan@example.com": [
        ("Interstellar", 5), ("Inception", 5), ("The Dark Knight", 5),
        ("The Prestige", 4), ("Arrival", 5), ("Blade Runner 2049", 4),
        ("Dunkirk", 4), ("Oppenheimer", 5), ("Sicario", 4), ("Gravity", 3),
    ],
    "crime.watcher@example.com": [
        ("Se7en", 5), ("Goodfellas", 5), ("The Departed", 4), ("Pulp Fiction", 5),
        ("Heat", 4), ("No Country for Old Men", 5), ("Gone Girl", 4),
        ("Prisoners", 4), ("Drive", 4), ("Oldboy", 5), ("Memories of Murder", 5),
    ],
    "animation.buff@example.com": [
        ("Spirited Away", 5), ("Princess Mononoke", 5), ("Your Name.", 5),
        ("Coco", 4), ("Spider-Man: Into the Spider-Verse", 5),
        ("Grave of the Fireflies", 4), ("The Wild Robot", 5),
    ],
    "admin@example.com": [
        ("Interstellar", 4), ("Parasite", 5), ("The Matrix", 5),
        ("Ex Machina", 4), ("Arrival", 4), ("Dune", 4),
    ],
}

# A "twin" of nolan.fan used to prove collaborative filtering surfaces a title
# the target user has NOT seen. Overlaps heavily, plus rates Dune highly.
ACTIVITY["twin.taste@example.com"] = [
    ("Interstellar", 5), ("Inception", 5), ("The Dark Knight", 4),
    ("Arrival", 5), ("Blade Runner 2049", 5), ("Oppenheimer", 4),
    ("Dune", 5), ("The Matrix", 5), ("Ex Machina", 4),
]
PERSONAS.append(("twin.taste@example.com", "Theo Lindqvist", ["ROLE_USER"],
                 ["Science Fiction", "Thriller"], "taste twin of nina, seeds collaborative signal"))

REVIEWS = [
    ("nolan.fan@example.com", "Interstellar", "A staggering achievement",
     "The docking sequence alone justifies the runtime. Hans Zimmer's organ score turns orbital mechanics into something close to liturgy, and the film earns its emotional swings honestly."),
    ("crime.watcher@example.com", "Se7en", "Still the benchmark",
     "Fincher's rain-soaked city is a character in its own right. The restraint in what it chooses not to show is exactly why the ending still lands decades later."),
    ("animation.buff@example.com", "Spirited Away", "Endlessly re-watchable",
     "Every frame rewards attention. What impresses most is how little it explains: the world simply exists, confident that curiosity will carry you through it."),
    ("admin@example.com", "Parasite", "Structurally perfect",
     "The tonal pivot at the midpoint is one of the most controlled shifts in modern cinema. It works because the class satire is never subordinated to the thriller mechanics."),
    ("nolan.fan@example.com", "Arrival", "Quiet science fiction done right",
     "A first-contact story with the patience to be about grammar and grief instead of firepower. Villeneuve trusts the audience completely."),
]


def build_activity() -> str:
    lines = [
        "-- Generated by tools/offline-verify/generate_seed.py - do not edit by hand.",
        "-- SYNTHETIC demo accounts and activity. No real personal data.",
        "-- Development password for every demo account is documented in the README.",
        "",
    ]
    for email, name, roles, fav_genres, note in PERSONAS:
        lines.append(f"-- {note}")
        lines.append(
            "INSERT INTO users (email, password_hash, display_name, enabled, email_verified, onboarding_completed) "
            f"VALUES ({q(email)}, {q(DEMO_PASSWORD_HASH)}, {q(name)}, TRUE, TRUE, "
            f"{q(bool(fav_genres))}) ON CONFLICT (email) DO NOTHING;")
        for role in roles:
            lines.append(
                "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r "
                f"WHERE u.email = {q(email)} AND r.name = {q(role)} ON CONFLICT DO NOTHING;")
        lines.append(
            "INSERT INTO user_preferences (user_id) SELECT id FROM users "
            f"WHERE email = {q(email)} ON CONFLICT DO NOTHING;")
        for i, g in enumerate(fav_genres):
            weight = round(1.0 - (i * 0.15), 3)
            lines.append(
                "INSERT INTO user_favourite_genres (user_id, genre_id, weight) "
                f"SELECT u.id, g.id, {weight} FROM users u, genres g "
                f"WHERE u.email = {q(email)} AND g.name = {q(g)} ON CONFLICT DO NOTHING;")
        lines.append("")

    lines.append("-- Ratings (drive the collaborative signal) -----------------------------")
    for email, entries in ACTIVITY.items():
        for title, score in entries:
            slug_title = q(title)
            lines.append(
                "INSERT INTO ratings (user_id, movie_id, score) SELECT u.id, m.id, "
                f"{score} FROM users u, movies m WHERE u.email = {q(email)} "
                f"AND m.title = {slug_title} ON CONFLICT (user_id, movie_id) DO NOTHING;")

    lines += ["", "-- Watch history -------------------------------------------------------"]
    for email, entries in ACTIVITY.items():
        for title, score in entries:
            for itype in ("VIEWED_DETAILS", "WATCHED_TRAILER", "COMPLETED", "RATED"):
                progress = 100 if itype == "COMPLETED" else "NULL"
                lines.append(
                    "INSERT INTO watch_history (user_id, movie_id, interaction_type, progress_percent, occurred_at) "
                    f"SELECT u.id, m.id, {q(itype)}, {progress}, NOW() - (random() * INTERVAL '60 days') "
                    f"FROM users u, movies m WHERE u.email = {q(email)} AND m.title = {q(title)};")

    lines += ["", "-- Watchlists ----------------------------------------------------------"]
    watchlists = {
        "nolan.fan@example.com": ["Dune", "Everything Everywhere All at Once", "Poor Things"],
        "crime.watcher@example.com": ["City of God", "Knives Out", "Anatomy of a Fall"],
        "animation.buff@example.com": ["Perfect Days", "Godzilla Minus One"],
        "newcomer@example.com": ["The Grand Budapest Hotel"],
    }
    for email, titles in watchlists.items():
        for t in titles:
            lines.append(
                "INSERT INTO watchlist_items (user_id, movie_id) SELECT u.id, m.id FROM users u, movies m "
                f"WHERE u.email = {q(email)} AND m.title = {q(t)} ON CONFLICT DO NOTHING;")

    lines += ["", "-- Favourite movies ----------------------------------------------------"]
    for email, entries in ACTIVITY.items():
        for title, score in entries:
            if score == 5:
                lines.append(
                    "INSERT INTO user_favourite_movies (user_id, movie_id) SELECT u.id, m.id "
                    f"FROM users u, movies m WHERE u.email = {q(email)} AND m.title = {q(title)} "
                    "ON CONFLICT DO NOTHING;")

    lines += ["", "-- Reviews -------------------------------------------------------------"]
    for email, title, rtitle, body in REVIEWS:
        lines.append(
            "INSERT INTO reviews (user_id, movie_id, title, body, status) SELECT u.id, m.id, "
            f"{q(rtitle)}, {q(body)}, 'PUBLISHED' FROM users u, movies m "
            f"WHERE u.email = {q(email)} AND m.title = {q(title)} ON CONFLICT DO NOTHING;")

    return "\n".join(lines) + "\n"


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "R__seed_catalogue.sql").write_text(build_catalogue())
    (OUT / "R__seed_demo_activity.sql").write_text(build_activity())
    print(f"films={len(FILMS)} genres={len(GENRES)} personas={len(PERSONAS)}")


if __name__ == "__main__":
    main()

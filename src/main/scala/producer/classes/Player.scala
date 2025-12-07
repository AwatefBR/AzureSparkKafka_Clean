package producer

class Player(
  val id: String,
  val overviewpage: String,
  val player: String,
  val image: String,
  val name: String,
  val nativename: String,
  val namealphabet: String,
  val namefull: String,
  val country: String,
  val nationality: String,
  val nationalityprimary: String,
  val age: String,
  val residencyformer: String,
  val team: String,
  val team2: String,
  val currentteams: String,
  val teamsystem: String,
  val team2system: String,
  val residency: String,
  val role: String,
  val favchamps: String,
  val birthdate: String,
  val deathdate: String
)

object Player {
  def apply(
    id: String, overviewpage: String, player: String, image: String, name: String,
    nativename: String, namealphabet: String, namefull: String, country: String,
    nationality: String, nationalityprimary: String, age: String, residencyformer: String, team: String, team2: String,
    currentteams: String, teamsystem: String, team2system: String, residency: String,
    role: String, favchamps: String, birthdate: String, deathdate: String
  ): Player =
    new Player(
      id, overviewpage, player, image, name, nativename, namealphabet, namefull,
      country, nationality, nationalityprimary, age, residencyformer, team, team2,
      currentteams, teamsystem, team2system, residency, role, favchamps, birthdate, deathdate
    )
}

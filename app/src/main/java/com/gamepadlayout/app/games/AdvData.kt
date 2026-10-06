package com.gamepadlayout.app.games

/** Interface que os dados do jogo (missões, diálogos, lojas) usam para falar com o estado do jogo sem depender dele. */
interface AdvCtx {
    fun has(id: String): Boolean
    fun count(id: String): Int
    fun flag(f: String): Boolean
    fun setFlag(f: String)
    fun give(id: String, n: Int = 1)
    fun take(id: String, n: Int = 1)
    fun addGems(n: Int)
    fun cnt(key: String): Int
    fun addCnt(key: String, n: Int = 1)
    fun toast(t: String)
    fun heal()
    fun openShop(which: Int)
    fun openForge()
    fun shards(): Int
}

/** Categorias da mochila. */
object Cat {
    const val ARMA = 0; const val EQUIP = 1; const val CONSUMO = 2; const val CHAVE = 3; const val MISSAO = 4; const val MATERIAL = 5
    val names = listOf("ARMAS", "EQUIPAR", "CONSUMIVEIS", "CHAVES", "MISSAO", "MATERIAIS")
}

/** slot: 0 nenhum, 1 escudo, 2 amuleto. rarity: 0 comum, 1 raro, 2 lendário. */
class ItemDef(val id: String, val name: String, val cat: Int, val desc: String, val stat: String = "", val rarity: Int = 0, val slot: Int = 0, val price: Int = 0)

/** Arma: ws = estilo de animação (WS), special = golpe especial (0 giro, 1 pancada no chão, 2 estocada, 3 rajada de flechas, 4 onda de energia). */
class WDef(
    val id: String, val ws: Int, val dmg: Float, val reach: Float, val dur: Float, val cost: Float, val h: Float,
    val heavy: Float, val special: Int, val trait: String, val parryBonus: Float = 0f, val comboLen: Int = 3, val ranged: Boolean = false
)

class ShDef(val id: String, val block: Float, val cost: Float, val parry: Float)

object AdvData {
    val items: Map<String, ItemDef> = listOf(
        // armas
        ItemDef("galho", "Espada de Galho", Cat.ARMA, "Cortada de um galho do carvalho-mestre. Velha, leve e teimosa. Dizem que lembra o caminho de volta para casa.", "Rapida. Dano baixo, gasta pouco folego."),
        ItemDef("espada", "Espada de Ferro", Cat.ARMA, "Forjada por Tonho na aldeia. Equilibrada e honesta, perdoa quem ainda esta aprendendo a apanhar golpes.", "Equilibrada. Aparar fica 0,03 s mais facil.", 0, 0, 180),
        ItemDef("machado", "Machado de Pedra", Cat.ARMA, "Pesado demais para quem tem pressa. Cada golpe pede respeito e folego.", "Lento e forte. O golpe forte quebra paredes rachadas e escudos.", 1),
        ItemDef("lanca", "Lanca do Vento", Cat.ARMA, "Leve como brisa de manha. Acerta antes que o inimigo perceba que voce chegou.", "Alcance longo. Especial: estocada que atravessa.", 1),
        ItemDef("ferrugem", "Lamina Ferrugem", Cat.ARMA, "Pertenceu ao Capitao que jurou proteger o Pico. A ferrugem nao a enfraqueceu: ela apenas lembra quanto custa a guarda.", "Dano alto. Aparar fica 0,05 s mais facil.", 2),
        ItemDef("aurora", "Lamina de Aurora", Cat.ARMA, "A espada do ultimo guardiao, limpa da ferrugem. Brilha quando o Vale respira.", "Dano muito alto. Golpes que acertam devolvem folego.", 2),
        ItemDef("cajado", "Cajado de Cristal", Cat.ARMA, "Um cristal preso em madeira antiga. Dispara esferas de luz que gastam folego em vez de forca.", "Ataque a distancia (esfera). Especial: onda de energia.", 2),
        ItemDef("arco", "Arco de Lia", Cat.ARMA, "Um arco curto de cacador. Cada flecha e uma conversa a distancia.", "Ataque a distancia. Usa flechas. Especial: rajada de tres.", 1),
        // escudos
        ItemDef("tabua", "Escudo de Tabua", Cat.EQUIP, "Feito com a porta do celeiro. Aguenta pancada de gosma e pouco mais.", "Bloqueia 55%. Gasta 16 de folego.", 0, 1),
        ItemDef("ferro", "Escudo de Ferro", Cat.EQUIP, "Forjado por Tonho depois da primeira ferrugem. Pesado, honesto, firme.", "Bloqueia 80%. Gasta 26 de folego.", 1, 1, 150),
        ItemDef("espelho", "Escudo Espelho", Cat.EQUIP, "Um pedaco da memoria da Sentinela. Quem olha demais ve o que esqueceu.", "Bloqueia 70%. Aparar fica 0,06 s mais facil.", 2, 1),
        ItemDef("ferrugem_s", "Escudo Ferrugem", Cat.EQUIP, "Um escudo de guardiao da cripta, carcomido mas teimoso.", "Bloqueia 75%. Gasta 18 de folego.", 1, 1),
        // amuletos
        ItemDef("folha", "Amuleto de Folha", Cat.EQUIP, "Uma folha que nunca seca. Faz o coracao bater mais devagar e mais forte.", "+25 de vida maxima.", 0, 2),
        ItemDef("brasa", "Amuleto de Brasa", Cat.EQUIP, "Uma brasa presa em resina. Esquenta a mao que segura a arma.", "+15% de dano.", 1, 2),
        ItemDef("pena", "Amuleto de Pena", Cat.EQUIP, "Pena de coruja velha. Quem a carrega respira fundo mais depressa.", "Folego volta 35% mais rapido.", 1, 2),
        ItemDef("trevo", "Amuleto de Trevo", Cat.EQUIP, "Quatro folhas, uma sorte teimosa.", "+25% de gemas ganhas.", 1, 2),
        ItemDef("vale", "Amuleto do Vale", Cat.EQUIP, "Guardado pelo Guarda-Cristal por mil anos. Faz o tempo desacelerar por um instante antes do golpe.", "Aparar fica 0,05 s mais facil.", 2, 2),
        // consumíveis
        ItemDef("seiva", "Seiva de Aurora", Cat.CONSUMO, "A seiva do carvalho-mestre guardada em um cantil. Cura quando a dor aperta. Enche de novo nas Brasas.", "Recupera 45% da vida."),
        ItemDef("bomba", "Bomba de Po", Cat.CONSUMO, "Po de pedra e fagulha. Faz um barulho que o Vale inteiro ouve.", "Explode em area. Abre paredes rachadas.", 0, 0, 22),
        ItemDef("flecha", "Flecha", Cat.CONSUMO, "Haste de freixo, ponta de ferro, pena de coruja.", "Municao do arco.", 0, 0, 2),
        ItemDef("pedra", "Pedra de Afiar", Cat.CONSUMO, "Esfregue na lamina e ela morde mais fundo por um tempo.", "+30% de dano por 90 s.", 0, 0, 25),
        ItemDef("fruta", "Fruta-Coracao", Cat.CONSUMO, "Rara. Cresce so onde o Coracao ainda lembra de brilhar.", "+10 de vida maxima, para sempre.", 2),
        ItemDef("frasco", "Frasco Extra", Cat.CONSUMO, "Um cantil vazio, pronto para guardar mais seiva.", "+1 carga maxima de seiva.", 1),
        // chaves
        ItemDef("chave_celeiro", "Chave do Celeiro", Cat.CHAVE, "Chave do portao do Campo de Treino.", "Abre o Campo de Treino."),
        ItemDef("chave_ferro", "Chave de Ferro", Cat.CHAVE, "Pesada, fria, com um cheiro de cripta.", "Abre a Cripta das Ruinas."),
        ItemDef("chave_cristal", "Chave de Cristal", Cat.CHAVE, "Um cristal lapidado em forma de chave. Pulsa devagar.", "Abre a Caverna Cristalina."),
        // missão
        ItemDef("martelo", "Martelo do Tonho", Cat.MISSAO, "Cabo gasto, cabeca amassada. Tonho jura que nao vive sem ele.", "Missao: Martelo Perdido."),
        ItemDef("colar", "Colar de Lia", Cat.MISSAO, "Um colar de contas azuis, preso a uma concha.", "Missao: Colar de Lia."),
        ItemDef("frag_g", "Estilhaco Verde", Cat.MISSAO, "Fragmento do Coracao: a vida que insiste em brotar, mesmo na pedra.", "Estilhaco do Coracao.", 2),
        ItemDef("frag_b", "Estilhaco Azul", Cat.MISSAO, "Fragmento do Coracao: a memoria de tudo o que o Vale ja foi.", "Estilhaco do Coracao.", 2),
        ItemDef("frag_r", "Estilhaco Rubro", Cat.MISSAO, "Fragmento do Coracao: a coragem de ficar quando todos fogem.", "Estilhaco do Coracao.", 2),
        ItemDef("carta", "Carta da Vovo", Cat.MISSAO, "\"Kai, se voce le isto, ja e maior do que a ferrugem. Volte para casa. - Na\"", "Lembranca."),
        // materiais
        ItemDef("minerio", "Minerio Rubro", Cat.MATERIAL, "Metal vivo, quente ao toque. Tonho paga bem por ele.", "Usado na forja."),
        ItemDef("gosma", "Gosma Brilhante", Cat.MATERIAL, "Pegajosa, fria e estranhamente limpa.", "Material."),
        ItemDef("erva", "Erva de Aurora", Cat.MATERIAL, "Cheira a manha depois da chuva. A Vovo faz chas com ela.", "Material."),
        ItemDef("cristal", "Cristal Azul", Cat.MATERIAL, "Um caco de cristal da caverna. Frio, liso, quase vivo.", "Material.")
    ).associateBy { it.id }

    val weapons: Map<String, WDef> = listOf(
        WDef("galho", WS.SWORD, 14f, 2.7f, 0.38f, 12f, 0.40f, 2.2f, 0, "rapida"),
        WDef("espada", WS.SWORD, 21f, 3.0f, 0.42f, 15f, 0.42f, 2.2f, 0, "equilibrada", 0.03f),
        WDef("machado", WS.AXE, 28f, 3.0f, 0.66f, 24f, 0.5f, 2.7f, 1, "pesada", 0f, 2),
        WDef("lanca", WS.SPEAR, 18f, 4.4f, 0.46f, 16f, 0.45f, 2.0f, 2, "alcance", 0f, 2),
        WDef("ferrugem", WS.SWORD, 31f, 3.3f, 0.44f, 18f, 0.42f, 2.3f, 0, "afiada", 0.05f),
        WDef("aurora", WS.SWORD, 40f, 3.4f, 0.42f, 17f, 0.40f, 2.3f, 0, "luminosa", 0.04f),
        WDef("cajado", WS.STAFF, 16f, 20f, 0.50f, 12f, 0.45f, 1.6f, 4, "magica", 0f, 1, true),
        WDef("arco", WS.BOW, 15f, 26f, 0.55f, 8f, 0.40f, 1.7f, 3, "distancia", 0f, 1, true)
    ).associateBy { it.id }

    val shields: Map<String, ShDef> = listOf(
        ShDef("tabua", 0.55f, 16f, 0f), ShDef("ferro", 0.80f, 26f, 0f), ShDef("espelho", 0.70f, 12f, 0.06f), ShDef("ferrugem_s", 0.75f, 18f, 0.03f)
    ).associateBy { it.id }

    val weaponOrder = listOf("galho", "espada", "machado", "lanca", "ferrugem", "aurora", "cajado", "arco")
    val quickItems = listOf("seiva", "bomba", "pedra", "fruta", "frasco")

    class Loot(val id: String, val n: Int)
    class ChestLoot(val gems: Int, val items: List<Loot>)

    val chestLoot: Map<String, ChestLoot> = mapOf(
        "c_casa" to ChestLoot(0, listOf(Loot("bomba", 3))),
        "c_campina1" to ChestLoot(60, emptyList()),
        "c_campina2" to ChestLoot(0, listOf(Loot("pedra", 2))),
        "c_flor1" to ChestLoot(90, emptyList()),
        "c_flor2" to ChestLoot(0, listOf(Loot("brasa", 1))),
        "c_flor3" to ChestLoot(70, listOf(Loot("bomba", 2))),
        "c_martelo" to ChestLoot(0, listOf(Loot("martelo", 1))),
        "c_ruina1" to ChestLoot(0, listOf(Loot("machado", 1))),
        "c_ruina2" to ChestLoot(100, listOf(Loot("minerio", 1))),
        "c_ruina3" to ChestLoot(0, listOf(Loot("folha", 1))),
        "c_cripta" to ChestLoot(0, listOf(Loot("ferrugem_s", 1), Loot("fruta", 1))),
        "c_cripta2" to ChestLoot(150, listOf(Loot("minerio", 2))),
        "c_pico1" to ChestLoot(0, listOf(Loot("pedra", 3))),
        "c_pico2" to ChestLoot(120, listOf(Loot("bomba", 3))),
        "c_pico3" to ChestLoot(0, listOf(Loot("lanca", 1))),
        "c_lago1" to ChestLoot(80, listOf(Loot("erva", 1))),
        "c_colar" to ChestLoot(0, listOf(Loot("colar", 1))),
        "c_ilha" to ChestLoot(100, listOf(Loot("fruta", 1))),
        "c_caverna1" to ChestLoot(0, listOf(Loot("minerio", 3), Loot("cristal", 2))),
        "c_caverna2" to ChestLoot(0, listOf(Loot("bomba", 4), Loot("flecha", 15))),
        "c_cajado" to ChestLoot(0, listOf(Loot("cajado", 1))),
        "c_jardim1" to ChestLoot(0, listOf(Loot("trevo", 1), Loot("fruta", 1))),
        "c_jardim2" to ChestLoot(200, emptyList()),
        "c_santuario1" to ChestLoot(0, listOf(Loot("frasco", 1))),
        "c_santuario2" to ChestLoot(250, emptyList())
    )

    class ShopEntry(val id: String, val qty: Int, val price: Int)
    val shopTonho = listOf(ShopEntry("espada", 1, 180), ShopEntry("ferro", 1, 150), ShopEntry("bomba", 1, 22), ShopEntry("pedra", 1, 25))
    val shopLia = listOf(ShopEntry("bomba", 1, 22), ShopEntry("flecha", 10, 18), ShopEntry("pedra", 1, 25), ShopEntry("seiva", 0, 0))

    val prologue = listOf(
        "Durante mil anos o Vale de Aurora viveu sob a luz do Coracao de Cristal.",
        "Ate a noite sem lua em que o Coracao se partiu em tres estilhacos.",
        "Dali em diante a ferrugem desceu das montanhas. Ela come o brilho, a cor e a memoria de tudo o que toca.",
        "Voce e Kai, mensageiro da aldeia. Ninguem esperava que voce fosse o heroi. Mas a ferrugem nao pergunta quem esta pronto."
    )

    val epilogue = listOf(
        "O Coracao de Cristal voltou a brilhar sobre o Vale, inteiro como nunca esteve.",
        "Aurel, livre da ferrugem, reconheceu a luz e agradeceu em silencio, como so os guardioes sabem fazer.",
        "A ferrugem recuou. O verde voltou aos campos, o azul ao lago, o vermelho ao pico.",
        "Kai voltou a aldeia pelo mesmo caminho de terra. Nao era mais o mensageiro. Era a razao de o Vale ter manha.",
        "FIM - Obrigado por jogar o Vale de Aurora."
    )

    /** Dicas curtas mostradas uma vez, no momento certo. */
    val tips = mapOf(
        "tip_lock" to "Aperte L1 para travar a mira. Aperte de novo para soltar.",
        "tip_guard" to "Segure L2 para defender. Aperte L2 NA HORA do golpe para aparar.",
        "tip_roll" to "R1 rola e da um instante de invencibilidade. Parado, ele da um passo para tras.",
        "tip_heavy" to "Segure Y para carregar o golpe forte e solte para atacar.",
        "tip_special" to "R2 usa o especial da arma, gastando folego.",
        "tip_item" to "X usa o item escolhido. Cima e baixo trocam a arma, esquerda e direita trocam o item.",
        "tip_stun" to "Inimigo atordoado! Ataque agora: ele leva dano dobrado.",
        "tip_riposte" to "Aparou! Aperte A logo em seguida para o contra-ataque."
    )
}

// ---------------------------------------------------------------------------------------------- missões

class QStep(val text: String, val done: (AdvCtx) -> Boolean)

/** auto = termina e premia sozinha; senão precisa ser entregue ao NPC. killKind >= 0: conta mortes desse tipo enquanto a missão está ativa. */
class QDef(
    val id: String, val main: Boolean, val title: String, val giver: String, val summary: String,
    val steps: List<QStep>, val auto: Boolean, val gems: Int = 0, val items: List<AdvData.Loot> = emptyList(), val rewardText: String = "",
    val killKind: Int = -1
)

object AdvQuests {
    private fun zv(z: Int): (AdvCtx) -> Boolean = { it.flag("zv_$z") }

    val all: List<QDef> = listOf(
        QDef("M1", true, "Despertar", "Vovo Na", "A aldeia esta quieta demais. Fale com a Vovo Na.",
            listOf(QStep("Fale com a Vovo Na, na aldeia") { it.flag("met") }), true, 30, emptyList(), "30 gemas"),
        QDef("M2", true, "Campo de Treino", "Guarda Doria", "Antes de sair, aprenda a lutar. A Guarda Doria guarda a chave do campo.",
            listOf(
                QStep("Pegue a chave do campo com a Guarda Doria") { it.has("chave_celeiro") || it.flag("dr_celeiro") },
                QStep("Abra o portao do Campo de Treino") { it.flag("dr_celeiro") },
                QStep("Acerte o Instrutor 5 vezes") { it.flag("tr_hit") },
                QStep("Apare um golpe do Instrutor (L2 na hora certa)") { it.flag("tr_parry") }
            ), true, 100, listOf(AdvData.Loot("seiva", 0)), "100 gemas"),
        QDef("M3", true, "Estilhaco Verde", "Vovo Na", "Musgrim guarda o estilhaco verde, na Floresta Musgosa, a oeste.",
            listOf(QStep("Chegue a Floresta Musgosa (oeste)", zv(Z.FLORESTA)), QStep("Derrote Musgrim, o Troll") { it.flag("bossG") }), true, 0, emptyList(), "Estilhaco Verde"),
        QDef("M4", true, "Memoria das Ruinas", "Vovo Na", "A Sentinela Rachada guarda a memoria do Vale, nas Ruinas Cinzentas, ao norte.",
            listOf(QStep("Chegue as Ruinas Cinzentas (norte)", zv(Z.RUINAS)), QStep("Derrote a Sentinela Rachada") { it.flag("bossB") }), true, 0, emptyList(), "Estilhaco Azul"),
        QDef("M5", true, "Coragem no Pico", "Vovo Na", "O Capitao Ferrugem ainda guarda o Pico Ferrugem, a leste.",
            listOf(QStep("Chegue ao Pico Ferrugem (leste)", zv(Z.PICO)), QStep("Derrote o Capitao Ferrugem") { it.flag("bossR") }), true, 0, emptyList(), "Estilhaco Rubro"),
        QDef("M6", true, "O Coracao de Cristal", "Vovo Na", "Com os tres estilhacos, o Portao do Coracao se abre.",
            listOf(
                QStep("Reuna os tres estilhacos (${"$"}n/3)") { it.shards() >= 3 },
                QStep("Abra o Portao do Coracao, ao norte da encruzilhada") { it.flag("dr_coracao") },
                QStep("Derrote Aurel, o Rei Ferrugem") { it.flag("bossA") }
            ), true, 0, emptyList(), "O fim da ferrugem"),

        QDef("S1", false, "Ervas da Vovo", "Vovo Na", "A Vovo precisa de 3 ervas de aurora para o cha. Elas crescem nas campinas.",
            listOf(QStep("Colete 3 ervas de aurora") { it.count("erva") >= 3 }), false, 60, listOf(AdvData.Loot("frasco", 1)), "Frasco Extra e 60 gemas"),
        QDef("S2", false, "Martelo Perdido", "Tonho Bigorna", "Tonho perdeu o martelo na Floresta Musgosa. Ele nao bate ferro sem ele.",
            listOf(QStep("Encontre o martelo na Floresta") { it.has("martelo") }), false, 80, listOf(AdvData.Loot("chave_ferro", 1)), "Chave de Ferro e 80 gemas"),
        QDef("S3", false, "Colar de Lia", "Lia", "Lia deixou cair o colar perto do Lago Espelho.",
            listOf(QStep("Encontre o colar perto do Lago") { it.has("colar") }), false, 60, listOf(AdvData.Loot("arco", 1), AdvData.Loot("flecha", 12)), "Arco de Lia, 12 flechas e 60 gemas"),
        QDef("S4", false, "Rãs-Ferrugem", "Barnabe", "As rãs ferrugentas assustam os peixes do lago. Barnabe quer 5 delas longe dali.",
            listOf(QStep("Derrote 5 rãs ferrugentas") { it.cnt("kq_S4") >= 5 }), false, 80, listOf(AdvData.Loot("chave_cristal", 1)), "Chave de Cristal e 80 gemas", 6),
        QDef("S5", false, "Cacador de Gosmas", "Mestre Beto", "Mestre Beto estuda gosmas e precisa de 8 amostras brilhantes.",
            listOf(QStep("Colete 8 gosmas brilhantes") { it.count("gosma") >= 8 }), false, 50, listOf(AdvData.Loot("pena", 1)), "Amuleto de Pena e 50 gemas"),
        QDef("S6", false, "Segredo de Mira", "Mira", "Mira ouviu falar de um jardim escondido atras de uma parede rachada. Ela deu bombas para abri-la.",
            listOf(QStep("Abra a parede rachada do Jardim Secreto (use bomba)") { it.flag("dr_jardim") }, QStep("Abra o bau do Jardim") { it.flag("ch_c_jardim1") }), true, 120, emptyList(), "120 gemas"),
        QDef("S7", false, "Ecos do Vale", "Piu", "Quatro Ecos antigos guardam dicas pelo Vale. Piu quer que voce os escute.",
            listOf(QStep("Escute os 4 Ecos") { it.cnt("eco") >= 4 }), true, 150, listOf(AdvData.Loot("fruta", 1)), "Fruta-Coracao e 150 gemas")
    )

    val byId: Map<String, QDef> = all.associateBy { it.id }

    /** 0 não iniciada, 1 ativa, 2 pronta para entregar, 3 concluída. */
    fun state(q: QDef, c: AdvCtx): Int {
        if (c.flag("qd_${q.id}")) return 3
        if (!q.main && !c.flag("q_${q.id}")) return 0
        val ok = q.steps.all { it.done(c) }
        return if (ok) 2 else 1
    }

    fun stepText(q: QDef, c: AdvCtx): String {
        val s = q.steps.firstOrNull { !it.done(c) } ?: return "Missao pronta para entregar"
        return s.text.replace("\$n", c.shards().toString())
            .let { if (q.id == "S1") "$it (${minOf(3, c.count("erva"))}/3)" else it }
            .let { if (q.id == "S4") "$it (${minOf(5, c.cnt("kq_S4"))}/5)" else it }
            .let { if (q.id == "S5") "$it (${minOf(8, c.count("gosma"))}/8)" else it }
            .let { if (q.id == "S7") "$it (${minOf(4, c.cnt("eco"))}/4)" else it }
    }

    fun active(c: AdvCtx): List<QDef> = all.filter { val s = state(it, c); s == 1 || s == 2 }

    /** A missão principal em andamento (a primeira que ainda não terminou, na ordem). */
    fun currentMain(c: AdvCtx): QDef? = all.firstOrNull { it.main && state(it, c) != 3 }
}

// ---------------------------------------------------------------------------------------------- diálogos

/** emo: 0 neutro, 1 alegre, 2 preocupado, 3 bravo, 4 triste. */
class Ln(val text: String, val emo: Int = 0)

class Convo(val who: String, val lines: List<Ln>, val choices: List<Pair<String, Convo?>> = emptyList(), val onEnd: ((AdvCtx) -> Unit)? = null)

object AdvDialogs {
    private fun c(who: String, vararg l: Ln, choices: List<Pair<String, Convo?>> = emptyList(), end: ((AdvCtx) -> Unit)? = null) = Convo(who, l.toList(), choices, end)
    private fun l(t: String, e: Int = 0) = Ln(t, e)

    private fun turnIn(q: String, who: String, c: AdvCtx, thanks: String, take: List<AdvData.Loot> = emptyList()): Convo {
        val d = AdvQuests.byId[q]!!
        return Convo(who, listOf(l(thanks, 1), l("Recompensa: ${d.rewardText}.", 1))) { x ->
            for (t in take) x.take(t.id, t.n)
            x.setFlag("qd_$q"); x.addGems(d.gems)
            for (it in d.items) if (it.n > 0) x.give(it.id, it.n)
            x.toast("Missao concluida: ${d.title}")
        }
    }

    private fun startQuest(q: String, x: AdvCtx) { x.setFlag("q_$q"); x.toast("Nova missao: ${AdvQuests.byId[q]!!.title}") }

    fun talk(npc: Int, x: AdvCtx): Convo = when (npc) {
        0 -> na(x)
        1 -> tonho(x)
        2 -> lia(x)
        3 -> piu(x)
        4 -> beto(x)
        5 -> doria(x)
        6 -> kiki(x)
        7 -> barnabe(x)
        8 -> mira(x)
        9, 10, 11, 12 -> eco(npc - 9, x)
        else -> noa(x)
    }

    private fun na(x: AdvCtx): Convo {
        val who = "Vovo Na"
        if (!x.flag("met")) return c(who,
            l("Kai! Finalmente acordou. O Vale esta doente, meu bem.", 2),
            l("Faz tres noites que o Coracao de Cristal se partiu. A ferrugem desce das montanhas e engole tudo o que brilha.", 2),
            l("Leve a Espada de Galho e o escudo do seu avo. Sao velhos, mas lembram o caminho."),
            l("Tres estilhacos sustentam o Coracao: um na Floresta Musgosa, a oeste; um nas Ruinas Cinzentas, ao norte; um no Pico Ferrugem, a leste."),
            l("Antes de ir, passe no campo de treino, ao norte da aldeia. A Guarda Doria cuida dele. E descanse nas Brasas: elas guardam o seu caminho.", 1),
            end = { it.setFlag("met"); it.give("carta") })
        if (x.flag("bossA")) return c(who, l("O Vale amanheceu, Kai. Tonho ja esta batendo ferro de novo.", 1), l("Se um dia a ferrugem voltar, a gente sabe a quem chamar.", 1))
        if (x.flag("dr_coracao")) return c(who,
            l("O Portao do Coracao se abriu. Eu senti daqui.", 2),
            l("O Rei Ferrugem tinha nome: Aurel, o ultimo guardiao do Coracao. Quando o Coracao rachou, ele tentou segurar sozinho.", 4),
            l("A ferrugem entrou nele em vez de entrar no Vale. Nao o odeie. Liberte-o.", 4))
        // missão das ervas
        val s1 = AdvQuests.byId["S1"]!!
        val s1s = AdvQuests.state(s1, x)
        if (s1s == 2) return turnIn("S1", who, x, "Ervas de aurora! Vou fazer um cha que cura ate saudade. Pegue este frasco.", listOf(AdvData.Loot("erva", 3)))
        val hub = mutableListOf<Pair<String, Convo?>>()
        if (s1s == 0) hub.add("Posso ajudar com algo?" to c(who, l("Que menino bom. Preciso de 3 ervas de aurora para o cha. Elas crescem nas campinas, onde o capim e mais claro.", 1), end = { startQuest("S1", it) }))
        if (s1s == 1) hub.add("Onde acho as ervas?" to c(who, l("Nas campinas, perto das estradas. Procure pontinhos claros no capim. Aperte A perto delas.")))
        hub.add("Como estou indo?" to c(who, *progressLines(x).toTypedArray()))
        hub.add("Ate logo." to null)
        return Convo(who, listOf(l(if (x.shards() == 0) "Comece pela Floresta, a oeste. Musgrim guarda o estilhaco verde e e mais teimoso que bonito." else "Voce trouxe ${x.shards()} estilhaco(s). O Coracao ja respira um pouco melhor.", if (x.shards() == 0) 0 else 1)), hub)
    }

    private fun progressLines(x: AdvCtx): List<Ln> {
        val m = AdvQuests.currentMain(x)
        val out = mutableListOf(l("Estilhacos: ${x.shards()} de 3."))
        if (x.shards() >= 3) out.add(l("Leve-os ao Portao do Coracao, ao norte da encruzilhada. Ele reconhece o que e dele.", 1))
        else if (m != null) out.add(l("Proximo passo: ${AdvQuests.stepText(m, x)}."))
        out.add(l("Se a coisa apertar, role. Escudo e para os corajosos, rolar e para os vivos."))
        return out
    }

    private fun tonho(x: AdvCtx): Convo {
        val who = "Tonho Bigorna"
        val s2 = AdvQuests.byId["S2"]!!
        val st = AdvQuests.state(s2, x)
        if (st == 2) return turnIn("S2", who, x, "Meu martelo! Achei que a floresta tinha comido. Pegue esta chave: abre a cripta das ruinas. La embaixo tem coisa boa e coisa feia.", listOf(AdvData.Loot("martelo", 1)))
        val ch = mutableListOf<Pair<String, Convo?>>()
        ch.add("Ver a loja" to c(who, l("Ferro bom, preco justo."), end = { it.openShop(0) }))
        ch.add("Forjar a arma" to c(who, l("Traga minerio rubro e gemas, e eu deixo sua lamina mais afiada."), end = { it.openForge() }))
        if (st == 0) ch.add("Algo errado?" to c(who, l("Perdi meu martelo! Fui buscar madeira na Floresta Musgosa e... pois e. Se voce achar, eu pago.", 2), end = { startQuest("S2", it) }))
        if (st == 1) ch.add("Sobre o martelo..." to c(who, l("A Floresta, a oeste. Procure em baus perto das trilhas. Cabo gasto, cabeca amassada.", 2)))
        ch.add("Ate logo." to null)
        return Convo(who, listOf(l(if (x.flag("bossA")) "O ferro ja nao enferruja. Isso nunca aconteceu na minha vida." else "Pode ir chegando. A forja esta quente.", if (x.flag("bossA")) 1 else 0)), ch)
    }

    private fun lia(x: AdvCtx): Convo {
        val who = "Lia"
        val s3 = AdvQuests.byId["S3"]!!
        val st = AdvQuests.state(s3, x)
        if (st == 2) return turnIn("S3", who, x, "Meu colar! Foi da minha mae. Fique com o meu arco: eu cacei muito com ele, mas voce vai cacar coisas maiores.", listOf(AdvData.Loot("colar", 1)))
        val ch = mutableListOf<Pair<String, Convo?>>()
        ch.add("Ver o que voce vende" to c(who, l("Bombas, flechas e pedras de afiar. Tudo que um mensageiro precisa!", 1), end = { it.openShop(1) }))
        if (st == 0) ch.add("Voce parece triste." to c(who, l("Perdi meu colar perto do Lago Espelho, a sudeste. Se achar, eu ficaria muito grata.", 4), end = { startQuest("S3", it) }))
        if (st == 1) ch.add("Sobre o colar..." to c(who, l("Perto do lago, a sudeste. Foi la que tropecei. Procure um bau.", 2)))
        ch.add("Ate logo." to null)
        return Convo(who, listOf(l("Oi, Kai! Dia bonito para nao ser engolido pela ferrugem, nao e?", 1)), ch)
    }

    private fun piu(x: AdvCtx): Convo {
        val who = "Piu"
        val st = AdvQuests.state(AdvQuests.byId["S7"]!!, x)
        val ch = mutableListOf<Pair<String, Convo?>>()
        ch.add("Me ensina a lutar!" to c(who,
            l("Hu-hu! Eu sou o Piu, a coruja da aldeia. Escuta bem.", 1),
            l("L1 trava a mira no inimigo. L2 levanta o escudo. Se voce apertar L2 NA HORA do golpe, voce apara: o inimigo fica tonto e voce contra-ataca com A."),
            l("R1 rola e da um instante de invencibilidade. Segure Y para um golpe forte. R2 e o especial da arma."),
            l("Cima e baixo trocam a arma. Esquerda e direita trocam o item. X usa o item."),
            l("Nas Brasas voce descansa, se fortalece e viaja. Mas descansar traz os monstros de volta!")))
        if (st == 0) ch.add("O que sao os Ecos?" to c(who, l("Quatro vozes antigas pelo Vale! Elas contam segredos de batalha. Escute todas e eu te dou um presente.", 1), end = { startQuest("S7", it) }))
        if (st == 1) ch.add("Ecos" to c(who, l("Falta(m) ${4 - x.cnt("eco")}. Tem um perto da encruzilhada, um nas ruinas, um no pico e um perto do Portao.", 1)))
        ch.add("Ate logo." to null)
        return Convo(who, listOf(l("Hu-hu! Voce e o Kai! Eu queria ser mensageiro tambem.", 1)), ch)
    }

    private fun beto(x: AdvCtx): Convo {
        val who = "Mestre Beto"
        val s5 = AdvQuests.byId["S5"]!!
        val st = AdvQuests.state(s5, x)
        if (st == 2) return turnIn("S5", who, x, "Oito amostras! Fascinante: a gosma brilha mais quando o dono esta com medo. Fique com este amuleto.", listOf(AdvData.Loot("gosma", 8)))
        if (st == 1) return c(who, l("Gosmas brilhantes caem das gosmas verdes e azuis. Faltam ${8 - minOf(8, x.count("gosma"))}. Nao vale as que eu ja guardo!", 0))
        return c(who, l("Shh. Estou varrendo a poeira e a ferrugem ao mesmo tempo. Nao e facil.", 0),
            l("Voce lida com gosmas? Eu estudo elas. Traga-me 8 gosmas brilhantes e eu lhe dou um amuleto que ja foi de um cavaleiro.", 1),
            end = { startQuest("S5", it) })
    }

    private fun doria(x: AdvCtx): Convo {
        val who = "Guarda Doria"
        if (!x.flag("met")) return c(who, l("Alto la! Sua avo ja falou com voce? Va falar com ela primeiro.", 3))
        if (!x.flag("dor_met") && !x.has("chave_celeiro") && !x.flag("dr_celeiro")) return c(who,
            l("Kai. A Vovo Na me disse que voce vai sair pelo Vale. Do jeito que esta, a ferrugem come voce no primeiro dia.", 2),
            l("O campo de treino, aqui ao norte, tem um instrutor que aguenta qualquer coisa. Pegue a chave e treine. Acerte nele, aprenda a aparar e a rolar."),
            l("Pare com o escudo erguido: segure L2 so para defender. Para aparar, aperte L2 no instante exato.", 0),
            end = { it.setFlag("dor_met"); it.give("chave_celeiro"); it.toast("Voce recebeu a Chave do Celeiro") })
        if (!x.flag("tr_parry") || !x.flag("tr_hit")) return c(who, l("O instrutor esta la dentro. Acerte nele 5 vezes e apare pelo menos um golpe. Quando ele levantar a arma com brilho amarelo, da para aparar. Brilho vermelho: role!"))
        return c(who, l("Voce esta pronto, Kai. Mais pronto que eu estava aos vinte.", 1), l("Cuidado nas estradas. Os monstros nao fazem fila."))
    }

    private fun kiki(x: AdvCtx): Convo {
        val opts = listOf(
            c("Kiki", l("Voce viu os coelhos? Eles pulam quando eu chego perto!", 1)),
            c("Kiki", l("Dizem que tem um jardim secreto atras de uma parede rachada. Eu nunca achei.", 1)),
            c("Kiki", l("A ferrugem nao come a gente quando a gente ri. Eu testei.", 1)),
            c("Kiki", l("O Tonho me deixou segurar o martelo uma vez. Era pesado!", 1)),
            c("Kiki", l("As ervas de aurora brilham mais cedo de manha.", 1))
        )
        return opts[(x.cnt("kiki")) % opts.size].also { x.addCnt("kiki") }
    }

    private fun barnabe(x: AdvCtx): Convo {
        val who = "Barnabe"
        val s4 = AdvQuests.byId["S4"]!!
        val st = AdvQuests.state(s4, x)
        if (st == 2) return turnIn("S4", who, x, "O lago respira! Tome esta chave de cristal: achei no fundo do lago anos atras. Abre a caverna ao leste do Vale.")
        if (st == 1) return c(who, l("Faltam ${5 - minOf(5, x.cnt("kq_S4"))} rãs ferrugentas. Elas ficam na beira da agua.", 3))
        return c(who, l("Silencio. Os peixes estao assustados.", 2),
            l("Rãs ferrugentas! Pulam, cospem, estragam a pesca. Se voce derrubar 5 delas eu lhe dou uma chave que achei no fundo do lago.", 3),
            end = { startQuest("S4", it) })
    }

    private fun mira(x: AdvCtx): Convo {
        val who = "Mira"
        val st = AdvQuests.state(AdvQuests.byId["S6"]!!, x)
        if (st == 0 && !x.flag("q_S6")) return c(who,
            l("Voce olha para essas pedras como se soubesse o que tem atras delas. Eu sei.", 2),
            l("Ao norte, na beira da floresta, existe uma parede rachada. Atras dela, um jardim que a ferrugem nunca achou."),
            l("Tome estas bombas. Acenda uma perto da parede, ou ataque ela com um golpe forte de machado.", 1),
            end = { it.setFlag("q_S6"); it.give("bomba", 4); it.toast("Nova missao: Segredo de Mira") })
        return c(who, l(if (x.flag("dr_jardim")) "O jardim existe, entao. Fico contente por alguem ter visto." else "A parede rachada fica ao norte, na beira da floresta. Ela range quando o vento passa.", 2))
    }

    private fun eco(i: Int, x: AdvCtx): Convo {
        val line = when (i) {
            0 -> if (x.flag("bossG")) listOf(l("Voce ja derrubou Musgrim. Mas lembre: o troll lento tambem bate. Aparar ou rolar, nunca ficar parado.")) else listOf(l("Eu sou Eco, o que sobrou de uma voz antiga."), l("Inimigos mostram o golpe antes de bater. Brilho amarelo: da para aparar com L2. Brilho vermelho: nao apare, role."))
            1 -> if (x.flag("bossB")) listOf(l("A Sentinela caiu. Ela guardava a memoria; agora a memoria guarda voce.")) else listOf(l("A Sentinela Rachada vive no fundo das ruinas. Ela investe em linha reta: desvie para o lado e castigue quando ela parar."), l("As esferas de cristal podem ser aparadas e devolvidas."))
            2 -> if (x.flag("bossR")) listOf(l("O Capitao enfim descansa. Ouvi um suspiro de alivio no vento.")) else listOf(l("O Pico Ferrugem sobe por aqui. O ar fica quente e as armaduras, vazias."), l("O Capitao gira a espada quando esta furioso. Corra para longe ou role para dentro do giro."))
            else -> listOf(l("Do outro lado deste portao ha um rei que ja foi guardiao."), l("Ele muda de forma tres vezes. Quando o ouro girar em volta dele, nao tente aparar: role."))
        }
        return Convo("Eco", line) { if (!it.flag("eco$i")) { it.setFlag("eco$i"); it.addCnt("eco"); it.addGems(15) } }
    }

    private fun noa(x: AdvCtx): Convo = c("Noa",
        l(if (x.flag("bossA")) "Olha so! O ceu esta mais azul! Obrigada, Kai!" else "Voce e o Kai! Vi voce rolar de cabeca! Ficou lindo.", 1),
        l("Dica de quem observa: se o inimigo brilhar de amarelo antes de bater, voce apara. Se brilhar vermelho, nem tenta!", 1))
}

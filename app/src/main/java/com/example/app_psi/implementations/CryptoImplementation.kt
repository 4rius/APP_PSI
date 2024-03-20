package com.example.app_psi.implementations

enum class CryptoImplementation(vararg aliases: String) {
    PAILLIER("Paillier", "Paillier OPE", "Paillier_OPE", "Paillier PSI-CA OPE"),
    DAMGARD_JURIK("DamgardJurik", "Damgard-Jurik", "DamgardJurik OPE", "Damgard-Jurik_OPE", "Damgard-Jurik OPE","DamgardJurik PSI-CA OPE", "Damgard-Jurik PSI-CA OPE");

    private val aliases: List<String>

    init {
        this.aliases = listOf(*aliases)
    }

    companion object {
        @JvmStatic
        fun fromString(text: String): CryptoImplementation? {
            for (ci in entries) {
                if (ci.aliases.contains(text)) {
                    return ci
                }
            }
            return null
        }
    }
}
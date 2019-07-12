package com.kanedasoftware.masterscrobbler.model


import com.google.gson.annotations.SerializedName

data class FanArtTvArtistInfo(
    val name: String = "",
    @SerializedName("mbid_id")
    val mbidId: String = "",
    val albums: Albums = Albums(),
    val hdmusiclogo: List<Hdmusiclogo> = listOf(),
    val artistbackground: List<Artistbackground> = listOf(),
    val artistthumb: List<Artistthumb> = listOf(),
    val musicbanner: List<Musicbanner> = listOf(),
    val musiclogo: List<Musiclogo> = listOf()
) {
    data class Musiclogo(
        val id: String = "",
        val url: String = "",
        val likes: String = ""
    )

    data class Artistbackground(
        val id: String = "",
        val url: String = "",
        val likes: String = ""
    )

    data class Albums(
        @SerializedName("f50fbcb4-bfcd-3784-b4c9-44f4793e66b2")
        val f50fbcb4Bfcd3784B4c944f4793e66b2: F50fbcb4Bfcd3784B4c944f4793e66b2 = F50fbcb4Bfcd3784B4c944f4793e66b2(),
        @SerializedName("97f37409-b91a-3495-8e70-56fd9f7658c2")
        val f37409B91a34958e7056fd9f7658c2: F37409B91a34958e7056fd9f7658c2 = F37409B91a34958e7056fd9f7658c2(),
        @SerializedName("8d18c070-adea-353f-a089-cbd2543342dd")
        val d18c070Adea353fA089Cbd2543342dd: D18c070Adea353fA089Cbd2543342dd = D18c070Adea353fA089Cbd2543342dd(),
        @SerializedName("72035143-d6ec-308b-8ee5-070b8703902a")
        val d6ec308b8ee5070b8703902a: D6ec308b8ee5070b8703902a = D6ec308b8ee5070b8703902a(),
        @SerializedName("3351730c-7853-38f3-8d56-1bf627de4523")
        val c785338f38d561bf627de4523: C785338f38d561bf627de4523 = C785338f38d561bf627de4523(),
        @SerializedName("709f8db6-2740-3a8b-b026-a08fb6171f81")
        val f8db627403a8bB026A08fb6171f81: F8db627403a8bB026A08fb6171f81 = F8db627403a8bB026A08fb6171f81(),
        @SerializedName("f1d65f47-f088-3cb2-a398-f5a685ddc344")
        val f1d65f47F0883cb2A398F5a685ddc344: F1d65f47F0883cb2A398F5a685ddc344 = F1d65f47F0883cb2A398F5a685ddc344(),
        @SerializedName("f1b9ee59-47b8-30d9-8c5d-5035812ba010")
        val f1b9ee5947b830d98c5d5035812ba010: F1b9ee5947b830d98c5d5035812ba010 = F1b9ee5947b830d98c5d5035812ba010(),
        @SerializedName("827eb91e-8acc-36e2-a576-3f1f55e6ebe2")
        val eb91e8acc36e2A5763f1f55e6ebe2: Eb91e8acc36e2A5763f1f55e6ebe2 = Eb91e8acc36e2A5763f1f55e6ebe2(),
        @SerializedName("a81c97f6-7a58-3a44-be4a-e0df67a83921")
        val a81c97f67a583a44Be4aE0df67a83921: A81c97f67a583a44Be4aE0df67a83921 = A81c97f67a583a44Be4aE0df67a83921(),
        @SerializedName("9cb50ffe-c5cf-338d-8833-f5fc5572f45f")
        val cb50ffeC5cf338d8833F5fc5572f45f: Cb50ffeC5cf338d8833F5fc5572f45f = Cb50ffeC5cf338d8833F5fc5572f45f(),
        @SerializedName("77b49885-13b4-47db-a26a-57879e981720")
        val b4988513b447dbA26a57879e981720: B4988513b447dbA26a57879e981720 = B4988513b447dbA26a57879e981720(),
        @SerializedName("21a4a256-7168-435e-af72-0352d0fa4521")
        val a4a2567168435eAf720352d0fa4521: A4a2567168435eAf720352d0fa4521 = A4a2567168435eAf720352d0fa4521()
    ) {
        data class D6ec308b8ee5070b8703902a(
            val cdart: List<Cdart> = listOf(),
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Cdart(
                val id: String = "",
                val url: String = "",
                val likes: String = "",
                val disc: String = "",
                val size: String = ""
            )

            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class Eb91e8acc36e2A5763f1f55e6ebe2(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class C785338f38d561bf627de4523(
            val cdart: List<Cdart> = listOf(),
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Cdart(
                val id: String = "",
                val url: String = "",
                val likes: String = "",
                val disc: String = "",
                val size: String = ""
            )

            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class Cb50ffeC5cf338d8833F5fc5572f45f(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class F1d65f47F0883cb2A398F5a685ddc344(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class B4988513b447dbA26a57879e981720(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class A4a2567168435eAf720352d0fa4521(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class D18c070Adea353fA089Cbd2543342dd(
            val albumcover: List<Albumcover> = listOf(),
            val cdart: List<Cdart> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )

            data class Cdart(
                val id: String = "",
                val url: String = "",
                val likes: String = "",
                val disc: String = "",
                val size: String = ""
            )
        }

        data class F50fbcb4Bfcd3784B4c944f4793e66b2(
            val albumcover: List<Albumcover> = listOf(),
            val cdart: List<Cdart> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )

            data class Cdart(
                val id: String = "",
                val url: String = "",
                val likes: String = "",
                val disc: String = "",
                val size: String = ""
            )
        }

        data class F8db627403a8bB026A08fb6171f81(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class A81c97f67a583a44Be4aE0df67a83921(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class F1b9ee5947b830d98c5d5035812ba010(
            val albumcover: List<Albumcover> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )
        }

        data class F37409B91a34958e7056fd9f7658c2(
            val albumcover: List<Albumcover> = listOf(),
            val cdart: List<Cdart> = listOf()
        ) {
            data class Albumcover(
                val id: String = "",
                val url: String = "",
                val likes: String = ""
            )

            data class Cdart(
                val id: String = "",
                val url: String = "",
                val likes: String = "",
                val disc: String = "",
                val size: String = ""
            )
        }
    }

    data class Musicbanner(
        val id: String = "",
        val url: String = "",
        val likes: String = ""
    )

    data class Hdmusiclogo(
        val id: String = "",
        val url: String = "",
        val likes: String = ""
    )

    data class Artistthumb(
        val id: String = "",
        val url: String = "",
        val likes: String = ""
    )
}
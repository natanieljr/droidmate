// DroidMate, an automated execution generator for Android apps.
// Copyright (C) 2012-2018. Saarland University
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// Current Maintainers:
// Nataniel Borges Jr. <nataniel dot borges at cispa dot saarland>
// Jenny Hotzkow <jenny dot hotzkow at cispa dot saarland>
//
// Former Maintainers:
// Konrad Jamrozik <jamrozik at st dot cs dot uni-saarland dot de>
//
// web: www.droidmate.org
package org.droidmate.test_tools.device_simulation

class GuiScreensBuilderFromSpec(private val timeGenerator: ITimeGenerator,
                                private val spec: String,
                                private val packageName: String) : IGuiScreensBuilder {
    override fun build(): List<IGuiScreen> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    // TODO Fix tests
    /*companion object {
        @JvmStatic
        private fun parseSpecEdges(spec: String): List<LabeledEdge<String, String, String>> {
            val specEdges: MutableList<LabeledEdge<String, String, String>> = mutableListOf()

            val matcher = "(\\w+)-(\\w+)->(\\w+) ?".toRegex().findAll(spec)

            matcher.forEach { m ->
                val edge = LabeledEdge(m.groups[1]!!.value, m.groups[2]!!.value, m.groups[3]!!.value)
                assert(edge.source !in GuiScreen.reservedIds)
                specEdges.add(edge)
            }

            assert(specEdges.size > 0, { "Expected to have at least one spec edge defined." })
            return specEdges
        }

        @JvmStatic
        private fun buildWidgetTransitions(edges: List<LabeledEdge<String, String, String>>, guiScreens: List<IGuiScreen>): List<LabeledEdge<String, String, String>>
        {
            edges.forEach {edge ->
                val sourceScreen = guiScreens.single {edge.source == it.getId()}
                val targetScreen = guiScreens.single {edge.target == it.getId()}
                sourceScreen.addWidgetTransition(edge.label, targetScreen)
            }

            return edges
        }

    }

    override fun build(): List<IGuiScreen> = buildGuiScreens(spec, packageName)

    private fun buildGuiScreens(spec: String, packageName: String): List<IGuiScreen> {
        val edges = parseSpecEdges(spec)

        val guiScreens = buildAppGuiScreens(edges, packageName)
        addSpecialGuiScreens(guiScreens)

        buildWidgetTransitions(edges, guiScreens)

        guiScreens.forEach {
            it.buildInternals()
            it.verify()
        }

        return guiScreens
    }

    private fun buildAppGuiScreens(edges: List<LabeledEdge<String, String, String>>, pkgName: String): MutableList<IGuiScreen> {
        val guiScreens = edges.map { edge ->
            arrayListOf(edge.source, edge.target).map { id ->
                if (id in GuiScreen.reservedIds)
                    null // Here we return null as Gui Screens having reserved ids do not belong to the app and so will be built by a different method.
                else
                    GuiScreen(id, pkgName, this.timeGenerator)
            }
        }.flatten().filterNotNull()

        assert(guiScreens.all { it.getId() !in GuiScreen.reservedIds })

        // Remove duplicate representations of the GuiScreens.
        return guiScreens
                .distinctBy { it.getId() }
                .toMutableList()
    }

    private fun addSpecialGuiScreens(guiScreens: MutableList<IGuiScreen>) {
        // The first GuiScreen is denoted as the one representing main activity, to be launched on app start.
        val main = guiScreens[0]
        val home = GuiScreen(GuiScreen.idHome, /* packageName */ "", this.timeGenerator)
        val chrome = GuiScreen(GuiScreen.idChrome, /* packageName */ "", this.timeGenerator)

        guiScreens.addAll(arrayListOf(home, chrome))

        guiScreens.forEach {
            it.addHomeScreenReference(home)
            it.addMainScreenReference(main)
        }
    }*/
}

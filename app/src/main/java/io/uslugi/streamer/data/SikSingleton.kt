package io.uslugi.streamer.data

import io.uslugi.streamer.helper.Constants

// It's only one instance of the class, that can be get, read and update from everywhere.
object SikSingleton {

    // Initial setups section with empty properties
    var section: Section = getInitialSection()

    // Reset section
    fun resetSection() {
        section = getInitialSection()
    }

    // Setup section
    fun setUpSection(newSection: Section) {
        section = newSection
    }

    // Get section
    fun getCurrentSection(): Section {
        return section
    }

    // Return empty section
    private fun getInitialSection() = Section(
        Constants.StringPlaceholders.EMPTY,
        Constants.StringPlaceholders.EMPTY,
        Constants.StringPlaceholders.EMPTY,
        Constants.StringPlaceholders.EMPTY
    )
}
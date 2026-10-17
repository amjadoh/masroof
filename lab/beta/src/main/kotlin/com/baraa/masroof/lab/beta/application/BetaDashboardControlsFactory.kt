package com.baraa.masroof.lab.beta.application

import com.baraa.masroof.lab.beta.BetaContainer

/** Shared independent-PR seam; the data track supplies its durable implementation. */
fun betaDashboardControls(container: BetaContainer): BetaDashboardControls = UnavailableBetaDashboardControls

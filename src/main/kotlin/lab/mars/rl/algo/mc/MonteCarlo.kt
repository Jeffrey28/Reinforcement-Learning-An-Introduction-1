package lab.mars.rl.algo.mc

import lab.mars.rl.model.MDP
import lab.mars.rl.model.NonDeterminedPolicy
import lab.mars.rl.util.emptyNSet
import org.slf4j.LoggerFactory

/**
 * <p>
 * Created on 2017-09-07.
 * </p>
 *
 * @author wumo
 */
class MonteCarlo(val mdp: MDP, var policy: NonDeterminedPolicy = emptyNSet()) {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java)!!
    }

    val gamma = mdp.gamma
    val started = mdp.started
    val states = mdp.states
    var episodes = 10000
    var epsilon = 0.1
}
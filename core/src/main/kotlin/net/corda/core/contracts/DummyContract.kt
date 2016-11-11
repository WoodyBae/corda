package net.corda.core.contracts

import net.corda.core.crypto.Party
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.TransactionBuilder

// The dummy contract doesn't do anything useful. It exists for testing purposes.

val DUMMY_PROGRAM_ID = DummyContract()

class DummyContract : Contract {

    interface State : ContractState {
        val magicNumber: Int
    }

    data class SingleOwnerState(override val magicNumber: Int = 0, override val owner: PublicKeyTree) : OwnableState, State {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKeyTree>
            get() = listOf(owner)

        override fun withNewOwner(newOwner: PublicKeyTree) = Pair(Commands.Move(), copy(owner = newOwner))
    }

    /**
     * Alternative state with multiple owners. This exists primarily to provide a dummy state with multiple
     * participants, and could in theory be merged with [SingleOwnerState] by putting the additional participants
     * in a different field, however this is a good example of a contract with multiple states.
     */
    data class MultiOwnerState(override val magicNumber: Int = 0,
                               val owners: List<PublicKeyTree>) : ContractState, State {
        override val contract = DUMMY_PROGRAM_ID
        override val participants: List<PublicKeyTree>
            get() = owners
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: TransactionForContract) {
        // Always accepts.
    }

    // The "empty contract"
    override val legalContractReference: SecureHash = SecureHash.sha256("")

    companion object {
        @JvmStatic
        fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
            val state = SingleOwnerState(magicNumber, owner.party.owningKey)
            return TransactionType.General.Builder(notary = notary).withItems(state, Command(Commands.Create(), owner.party.owningKey))
        }

        fun move(prior: StateAndRef<DummyContract.SingleOwnerState>, newOwner: PublicKeyTree) = move(listOf(prior), newOwner)
        fun move(priors: List<StateAndRef<DummyContract.SingleOwnerState>>, newOwner: PublicKeyTree): TransactionBuilder {
            require(priors.size > 0)
            val priorState = priors[0].state.data
            val (cmd, state) = priorState.withNewOwner(newOwner)
            return TransactionType.General.Builder(notary = priors[0].state.notary).withItems(
                    /* INPUTS  */ *priors.toTypedArray(),
                    /* COMMAND */ Command(cmd, priorState.owner),
                    /* OUTPUT  */ state
            )
        }
    }
}

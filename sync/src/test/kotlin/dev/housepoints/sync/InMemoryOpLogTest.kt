package dev.housepoints.sync

class InMemoryOpLogTest : OpLogContract() {
    override fun newLog(): OpLog = InMemoryOpLog()
}

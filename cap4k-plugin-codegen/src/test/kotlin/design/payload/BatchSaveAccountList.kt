package design.payload

class BatchSaveAccountList {

    data class Request(
        val globalId: Long,
        val accounts: List<Item>
    )

    data class Response(
        val result: Boolean,
    )

    data class Item(
        val accountNumber: String,
        val accountName: String,
        val bankName: String,
        val currency: String
    )
}

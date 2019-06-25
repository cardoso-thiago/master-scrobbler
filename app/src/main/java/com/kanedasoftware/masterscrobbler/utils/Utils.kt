package com.kanedasoftware.masterscrobbler.utils

import java.math.BigInteger
import java.security.MessageDigest
import java.util.logging.Logger

class Utils {
    companion object {
        fun getSig(params: Map<String, out String>, method: String):String{
            var hashmapParams = HashMap<String, String>(params)
            hashmapParams["api_key"] = Constants.API_KEY
            hashmapParams["method"] = method
            val sortedMap = hashmapParams.toSortedMap()


            var sigFormat = ""
            sortedMap.forEach { param ->
                sigFormat = sigFormat.plus(param.key).plus(param.value)
            }
            sigFormat = sigFormat.plus(Constants.SHARED_SECRET)

            return String.format("%032x", BigInteger(1, MessageDigest.getInstance("MD5").digest(sigFormat.toByteArray(Charsets.UTF_8))))
        }

        fun logDebug(message:String){
            Logger.getLogger(Constants.LOG_TAG).info(message)
        }
    }
}
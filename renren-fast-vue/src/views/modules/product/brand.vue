<!-- ============================================================
  品牌管理页面
  功能说明：
    1. 列表展示品牌信息（分页、搜索、批量删除）
    2. 支持新增/修改弹窗（通过 `add-or-update` 组件）
    3. 表格内展示品牌 logo（直接使用后端返回的 URL 回显）
    4. 支持通过开关修改品牌显示状态（并发送更新请求）
  数据来源：/product/brand/list 接口
  备注：logo 字段通常为 MinIO/S3 的 presigned URL，页面直接用 <img> 加载展示。
============================================================== -->
<template>
  <div class="mod-config">
    <el-form :inline="true" :model="dataForm" @keyup.enter.native="getDataList()">
      <el-form-item>
        <!-- 查询表单：按 key 模糊搜索，回车触发查询 -->
        <el-input v-model="dataForm.key" placeholder="参数名" clearable></el-input>
      </el-form-item>
      <el-form-item>
        <el-button @click="getDataList()">查询</el-button>
        <el-button v-if="isAuth('product:brand:save')" type="primary" @click="addOrUpdateHandle()">新增</el-button>
        <el-button v-if="isAuth('product:brand:delete')" type="danger" @click="deleteHandle()"
          :disabled="dataListSelections.length <= 0">批量删除</el-button>
      </el-form-item>
    </el-form>
    <!-- 品牌表格：支持多选、排序列、logo 回显、开关直接修改 showStatus -->
    <el-table :data="dataList" border v-loading="dataListLoading" @selection-change="selectionChangeHandle"
      style="width: 100%;">
      <el-table-column type="selection" header-align="center" align="center" width="50">
      </el-table-column>
      <el-table-column prop="brandId" header-align="center" align="center" label="品牌id">
      </el-table-column>
      <el-table-column prop="name" header-align="center" align="center" label="品牌名">
      </el-table-column>
      <el-table-column prop="logo" header-align="center" align="center" label="品牌logo">
        <template slot-scope="scope">
          <!-- logo 回显：后端返回的 URL（通常为 presigned GET URL），直接赋值给 img.src 即可展示 -->
          <!-- 如果需要对图片进行二次处理（如裁剪/读取像素），需关心 CORS 策略（minIO 上需配置 CORS） -->
          <img :src="scope.row.logo" style="width: 100px; height: 80px">
        </template>
      </el-table-column>
      <el-table-column prop="descript" header-align="center" align="center" label="介绍">
      </el-table-column>
      <el-table-column prop="showStatus" header-align="center" align="center" label="显示状态">
        <template slot-scope="scope">
          <!-- 显示文字状态 -->
          <el-tag :type="scope.row.showStatus === 1 ? 'success' : 'danger'">
            {{ scope.row.showStatus === 1 ? '显示' : '隐藏' }}
          </el-tag>
          <!-- 开关 -->
          <el-switch v-model="scope.row.showStatus" active-color="#13ce66" inactive-color="#ff4949"
            @change="updateBrandShowStatus(scope.row)" :active-value="1" :inactive-value="0">
          </el-switch>
        </template>
      </el-table-column>
      <el-table-column prop="firstLetter" header-align="center" align="center" label="检索首字母">
      </el-table-column>
      <el-table-column prop="sort" header-align="center" align="center" label="排序">
      </el-table-column>
      <el-table-column fixed="right" header-align="center" align="center" width="150" label="操作">
        <template slot-scope="scope">
          <el-button type="text" size="small" @click="addOrUpdateHandle(scope.row.brandId)">修改</el-button>
          <el-button type="text" size="small" @click="deleteHandle(scope.row.brandId)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination @size-change="sizeChangeHandle" @current-change="currentChangeHandle" :current-page="pageIndex"
      :page-sizes="[10, 20, 50, 100]" :page-size="pageSize" :total="totalPage"
      layout="total, sizes, prev, pager, next, jumper">
    </el-pagination>
    <!-- 弹窗, 新增 / 修改 -->
    <add-or-update v-if="addOrUpdateVisible" ref="addOrUpdate" @refreshDataList="getDataList"
      @closed="addOrUpdateVisible = false"></add-or-update>
  </div>
</template>

<script>
import AddOrUpdate from './brand-add-or-update'
export default {
  data() {
    return {
      dataForm: {
        key: ''
      },
      dataList: [],
      pageIndex: 1,
      pageSize: 10,
      totalPage: 0,
      dataListLoading: false,
      dataListSelections: [],
      addOrUpdateVisible: false
    }
  },
  components: {
    AddOrUpdate
  },
  activated() {
    this.getDataList()
  },
  methods: {
    /**
     * 获取数据列表
     * - 发起 GET 请求到 /product/brand/list，带分页和查询参数
     * - 成功时将返回的数据赋给 `dataList` 与 `totalPage`
     * - 在控制台打印请求参数和后端原始响应，便于追踪数据库返回
     */
    getDataList() {
      console.log('getDataList - request', { page: this.pageIndex, limit: this.pageSize, key: this.dataForm.key })
      this.dataListLoading = true
      /**
       * 请求：调用后端列表接口，adornParams 会包装公共参数（时间戳、签名等）
       */
      this.$http({
        url: this.$http.adornUrl('/product/brand/list'),
        method: 'get',
        params: this.$http.adornParams({
          'page': this.pageIndex,
          'limit': this.pageSize,
          'key': this.dataForm.key
        })
      }).then(({ data }) => {
        /**
         * 响应处理：打印后端原始返回结果，便于在控制台查看数据库返回内容
         */
        console.log('getDataList - response', data)
        if (data && data.code === 0) {
          // 成功解析页面数据并赋值给表格
          this.dataList = data.page.list
          this.totalPage = data.page.totalCount
        } else {
          // 接口异常或返回为空时清空列表
          this.dataList = []
          this.totalPage = 0
        }
        this.dataListLoading = false
      })
    },
    // 每页数
    /**
     * 每页显示数量变化处理
     * @param {Number} val - 新的每页数量
     */
    sizeChangeHandle(val) {
      this.pageSize = val
      this.pageIndex = 1
      this.getDataList()
    },
    // 当前页
    /**
     * 当前页变化处理
     * @param {Number} val - 新的页码
     */
    currentChangeHandle(val) {
      this.pageIndex = val
      this.getDataList()
    },
    /**
     * 多选回调
     * @param {Array} val - 当前被选中的行数据数组
     * 在控制台打印被选中项，便于调试批量删除等操作
     */
    selectionChangeHandle(val) {
      console.log('selectionChangeHandle - selections', val)
      this.dataListSelections = val
    },
    /**
     * 打开新增/修改弹窗
     * @param {Number} id - 品牌 id（传入则为编辑，否则为新增）
     * 在控制台记录被编辑的 id，调用子组件的 init 方法完成回显
     */
    addOrUpdateHandle(id) {
      console.log('addOrUpdateHandle - open dialog', { id: id })
      this.addOrUpdateVisible = true
      this.$nextTick(() => {
        this.$refs.addOrUpdate.init(id)
      })
    },
    /**
     * 删除单条或批量删除
     * @param {Number|undefined} id - 单条删除时传 id，批量删除时由 dataListSelections 计算 ids
     * 在控制台记录将要删除的 ids，并打印后端响应以便查看数据库删除结果
     */
    deleteHandle(id) {
      var ids = id ? [id] : this.dataListSelections.map(item => {
        return item.brandId
      })
      console.log('deleteHandle - will delete ids', ids)
      this.$confirm(`确定对[id=${ids.join(',')}]进行[${id ? '删除' : '批量删除'}]操作?`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$http({
          url: this.$http.adornUrl('/product/brand/delete'),
          method: 'post',
          data: this.$http.adornData(ids, false)
        }).then(({ data }) => {
          /**
           * 后端响应：打印删除接口返回内容
           */
          console.log('deleteHandle - response', data)
          if (data && data.code === 0) {
            this.$message({
              message: '操作成功',
              type: 'success',
              duration: 1500,
              onClose: () => {
                this.getDataList()
              }
            })
          } else {
            this.$message.error(data.msg)
          }
        })
      })
    },

    /**
     * 切换品牌显示状态
     * @param {Object} data - 行数据，至少包含 brandId 与 showStatus
     * 在控制台打印请求前后的信息，便于追踪更新是否在数据库生效
     */
    updateBrandShowStatus(data) {
      console.log('updateBrandShowStatus - request', { brandId: data.brandId, showStatus: data.showStatus })
      // 或者解构数据发送
      // let { brandId, showStatus } = data
      this.$http({
        url: this.$http.adornUrl('/product/brand/update/status'),
        method: 'post',
        data: this.$http.adornData({
          'brandId': data.brandId,
          'showStatus': data.showStatus
        }, false)
      }).then(({ data }) => {
        /**
         * 后端返回：打印更新接口返回的完整数据
         */
        console.log('updateBrandShowStatus - response', data)
        if (data && data.code === 0) {
          this.$message({
            message: '操作成功',
            type: 'success',
            duration: 1500,
            onClose: () => {
              this.getDataList()
            }
          })
        } else {
          this.$message.error(data.msg)
        }
      })
    }
  }
}
</script>

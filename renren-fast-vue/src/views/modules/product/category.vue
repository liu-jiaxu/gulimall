<!-- ============================================================
  商品三级分类管理页面
  功能说明：
    1. 以树形结构展示商品分类（一级 / 二级 / 三级）
    2. 勾选节点后，可对前两级节点进行新增（Append）操作
    3. 勾选叶子节点后，可对选中的分类进行批量删除（Delete）
  数据来源：/product/category/list/tree 接口
============================================================= -->
<template>
  <div>
    <!-- 分类树组件
       :data                    树形数据（menus）
       :props                   节点字段映射（children 子节点、name 显示名称）
       :expand-on-click-node    点击节点是否展开（false：不因点击展开）
       :show-checkbox           是否显示复选框
       node-key                 节点唯一标识字段（catId）
       ref                      组件引用名（用于调用树组件方法）
       :default-expanded-keys   默认展开的节点 key（catId）列表 -->
    <el-tree :data="menus" :props="defaultProps" :expand-on-click-node="false" :show-checkbox="true" node-key="catId"
      ref="menuTree" :default-expanded-keys="expandedKey">
      <!-- 自定义节点内容插槽：node 为树节点对象，data 为节点对应的数据 -->
      <span class="custom-tree-node" slot-scope="{ node, data }">
        <!-- 显示当前节点的分类名称 -->
        <span>{{ node.label }}</span>
        <span>
          <!-- 新增按钮：仅当节点被勾选 且 为前两级（level <= 2）时显示 -->
          <el-button v-if="node.checked && node.level <= 2" type="text" size="mini" @click="() => append(data)">
            Append
          </el-button>
          <!-- 删除按钮：仅当节点被勾选 且 为叶子节点（无子分类）时显示 -->
          <el-button v-if="node.checked && node.childNodes.length === 0" type="text" size="mini"
            @click="() => remove()">
            Delete
          </el-button>
          <!--编辑按钮-->
          <el-button v-if="node.checked" type="text" size="mini" @click="() => edit(data)">
            Edit
          </el-button>
        </span>
      </span>
    </el-tree>

    <!-- 新增分类对话框
         title          对话框标题
         :visible.sync  控制对话框显示/隐藏（与 dialogVisible 双向绑定） -->
    <el-dialog :title="title" :visible.sync="dialogVisible" width="30%" :close-on-click-modal="false">
      <!-- 新增分类的表单，模型为 category -->
      <el-form :model="category">
        <!-- 分类名称输入框，双向绑定 category.name -->
        <el-form-item label="分类名称">
          <el-input v-model="category.name" autocomplete="off"></el-input>
        </el-form-item>
        <!-- 图标输入框，双向绑定 category.icon -->
        <el-form-item label="图标">
          <el-input v-model="category.icon" autocomplete="off"></el-input>
        </el-form-item>
        <!-- 计量单位输入框，双向绑定 category.productUnit -->
        <el-form-item label="计量单位(个、件...)">
          <el-input v-model="category.productUnit" autocomplete="off"></el-input>
        </el-form-item>
        <!-- 排序序号输入框，双向绑定 category.sort -->
        <el-form-item label="排序序号（0-任意，值越大越靠前）">
          <el-input v-model="category.sort" autocomplete="off"></el-input>
        </el-form-item>
      </el-form>
      <!-- 对话框底部操作按钮区 -->
      <span slot="footer" class="dialog-footer">
        <!-- 取消按钮：关闭对话框，不提交 -->
        <el-button @click="dialogVisible = false">取 消</el-button>
        <!-- 确定按钮：提交新增分类 -->
        <el-button type="primary" @click="sumbitData">确 定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
// 这里是组件的脚本逻辑部分，可以导入其他文件（组件、工具 js、json、图片等）

// 导出当前组件的配置对象
export default {
  // 局部注册的子组件（当前页面暂无子组件）
  components: {},

  // 组件响应式数据
  data() {
    return {
      // 分类树数据（后端返回的树形结构）
      menus: [],

      // 对话框标题（新增时"添加分类"，修改时"修改分类"）
      title: "",
      // 对话框类型：add 表示新增，edit 表示修改
      dialogType: "",

      // 新增分类的表单数据（字段与数据库 pms_category 表对应）
      // name：分类名称；parentCid：父分类 id；catLevel：层级；showStatus：是否显示；sort：排序
      category: { name: "", parentCid: 0, catLevel: 0, showStatus: 1, sort: 0, catId: null, icon: "", productUnit: "" },
      // 新增分类对话框是否显示（true 显示，false 隐藏）
      dialogVisible: false,

      // 默认展开的节点 key（catId）数组，新增/删除后用于展开指定分类
      expandedKey: [],

      // 树节点字段映射配置
      defaultProps: {
        // 子节点字段名
        children: 'children',
        // 节点显示名称字段名
        label: 'name'
      }
    }
  },

  // 组件方法
  methods: {
    // 从后端获取商品分类树形数据
    getMenus() {
      // 发起请求：调用商品分类列表（树形）接口
      this.$http({
        url: this.$http.adornUrl('/product/category/list/tree'),
        method: 'get'
      }).then(({ data }) => {
        // 请求成功：把返回的分类树数据存入 menus
        this.menus = data.data
        console.log('成功了获取到菜单数据....', data.data)
      })
    },

    // 对话框"确定"按钮的统一入口：根据对话框类型分发到新增或修改方法
    sumbitData() {
      if (this.dialogType == "add") {
        this.addCategory();
      } else if (this.dialogType == "edit") {
        this.editCategory();
      }
    },

    // 点击某个节点的 Append 按钮后触发：打开新增对话框并预填父分类信息
    append(data) {
      console.log("append", data);
      this.dialogType = "add"
      this.title = "添加分类"
      // 显示新增分类对话框
      this.dialogVisible = true;
      // 记录新分类的父分类 id（表示在该节点下新增子分类）
      this.category.parentCid = data.catId;
      // 计算新分类的层级：当前节点层级 + 1
      this.category.catLevel = data.catLevel * 1 + 1;

      // 其余属性改为默认值，防止修改操作赋值影响
      this.category.name = ""
      this.category.catId = null
      this.category.sort = 0
      this.category.icon = ""
      this.category.productUnit = ""
      this.category.showStatus = 1
    },

    // 点击对话框"确定"按钮后触发：提交新增分类请求
    addCategory() {
      console.log("提交的数据", this.category);
      // 发起请求：调用商品分类保存接口
      this.$http({
        url: this.$http.adornUrl("/product/category/save"),
        method: "post",
        data: this.$http.adornData(this.category, false),
      }).then(() => {
        // 保存成功：弹出成功提示
        this.$message({
          message: "添加成功",
          type: "success",
        });
        // 刷新出新的菜单（重新拉取分类树）
        this.getMenus();
        // 设置需要默认展开的菜单（展开新分类的父分类）
        this.expandedKey = [this.category.parentCid];
        // 关闭新增分类对话框
        this.dialogVisible = false;
      }).catch(() => {
        // 保存失败：弹出错误提示，避免用户误以为添加成功
        this.$message({
          message: "添加失败",
          type: "error"
        });
      })
    },

    // 批量删除被勾选且为叶子节点的分类
    remove() {
      // 收集被勾选且是叶子节点（无子分类）的节点，以 {catId, name} 对象数组存储
      const removeList = []
      // 遍历所有被勾选节点的 id
      this.$refs.menuTree.getCheckedKeys().forEach(id => {
        // 根据 id 获取对应的树节点对象
        const checkedNode = this.$refs.menuTree.getNode(id)
        // 只统计叶子节点（没有子分类的节点才允许删除）
        if (checkedNode && checkedNode.childNodes.length === 0) {
          // 记录该节点的 id 与名称
          removeList.push({ catId: id, name: checkedNode.data.name })
        }
      })
      // 接口请求所需的纯 id 数组（从 removeList 中提取）
      const catIds = removeList.map(item => item.catId)
      // 弹出二次确认框，提示用户将删除的分类数量
      this.$confirm(`确定要删除选中的 ${catIds.length} 个分类吗?`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        // 用户点击"确定"后，发起批量删除请求
        this.$http({
          url: this.$http.adornUrl('/product/category/delete'),
          method: 'post',
          data: this.$http.adornData(catIds, false)
        }).then(({ data }) => {
          // 根据后端返回的 code 判断删除是否成功
          if (data && data.code === 0) {
            // 删除成功：弹出成功提示
            this.$message({
              message: '删除成功',
              type: 'success'
            })
            // 删除成功：重新拉取分类树以刷新页面数据
            this.getMenus()
            // 删除成功：遍历所有被删节点，收集需要展开的父/祖父节点 key
            const expandKeys = []
            removeList.forEach(item => {
              const node = this.$refs.menuTree.getNode(item.catId)
              if (node && node.parent) {
                const parent = node.parent
                // 父节点下是否还有未被删除的子节点（即该节点是否有同级子节点）
                const hasSibling = parent.childNodes.some(n => catIds.indexOf(n.data.catId) === -1)
                if (hasSibling) {
                  // 有同级子节点：展开父节点，展示到这些子节点
                  expandKeys.push(parent.data.catId)
                } else if (parent.parent && parent.parent.data.catId !== undefined) {
                  // 无同级子节点（父节点下已删光）：展开祖父节点，展示到上一级父节点
                  expandKeys.push(parent.parent.data.catId)
                }
              }
            })
            // 过滤无效 key 并去重，设置默认展开节点
            this.expandedKey = [...new Set(expandKeys.filter(k => k !== undefined && k !== null))]
            // 删除成功：打印本次删除的分类信息（id + name）
            console.log('removeInfo', removeList)
          } else {
            // 删除失败：弹出后端返回的错误信息
            this.$message({
              message: data.msg,
              type: 'error'
            })
          }
          // 无论成败，打印后端返回的完整结果
          console.log('removeResult', data)
        })
      }).catch(() => {
        // 用户点击"取消"，或请求过程中出错：不做任何处理
      })
    },

    // 点击某个节点的 Edit 按钮后触发：打开修改对话框并回显该分类的原始数据
    edit(data) {
      console.log("要修改的数据", data)
      this.dialogType = "edit"
      this.title = "修改分类"
      this.dialogVisible = true

      // 发送请求获取最新数据再修改，避免脏读
      this.$http({
        url: this.$http.adornUrl(`/product/category/info/${data.catId}`),
        method: 'get',
      }).then(({ data }) => {
        console.log("回显数据", data)
        // parentCid: 0, catLevel: 0, showStatus: 1, 
        this.category.name = data.data.name
        this.category.catId = data.data.catId
        this.category.sort = data.data.sort
        this.category.icon = data.data.icon
        this.category.productUnit = data.data.productUnit
        // 部分数据需要设置原值防止修改
        this.category.parentCid = data.data.parentCid
        this.category.catLevel = data.data.catLevel
        this.category.showStatus = data.data.showStatus
      })
    },

    // 提交修改分类请求（通过 update 接口更新该分类数据）
    editCategory() {
      console.log("修改的数据", this.category);
      // 发起请求：调用商品分类更新接口
      this.$http({
        url: this.$http.adornUrl("/product/category/update"),
        method: "post",
        data: this.$http.adornData(this.category, false),
      }).then(() => {
        // 保存成功：弹出成功提示
        this.$message({
          message: "修改成功",
          type: "success",
        });
        // 刷新出新的菜单（重新拉取分类树）
        this.getMenus();
        // 设置需要默认展开的菜单（展开新分类的父分类）
        this.expandedKey = [this.category.parentCid];
        // 关闭修改分类对话框
        this.dialogVisible = false;
      }).catch(() => {
        // 保存失败：弹出错误提示，避免用户误以为修改成功
        this.$message({
          message: "修改失败",
          type: "error"
        });
      })
    },
  },

  // 计算属性（当前无）
  computed: {},

  // 侦听器：监控 data 中的数据变化（当前无）
  watch: {},

  // 生命周期钩子 - created：组件实例创建完成后触发
  created() {
    // 页面初始化时，拉取分类树数据
    this.getMenus()
  },

  // 生命周期钩子 - mounted：组件挂载到 DOM 后触发
  mounted() {

  },

  // 生命周期钩子 - beforeCreate：组件实例创建之前触发
  beforeCreate() { },
  // 生命周期钩子 - beforeMount：组件挂载之前触发
  beforeMount() { },
  // 生命周期钩子 - beforeUpdate：数据更新之前触发
  beforeUpdate() { },
  // 生命周期钩子 - updated：数据更新之后触发
  updated() { },
  // 生命周期钩子 - beforeDestroy：组件销毁之前触发
  beforeDestroy() { },
  // 生命周期钩子 - destroyed：组件销毁完成后触发
  destroyed() { },
  // 生命周期钩子 - activated：页面被 keep-alive 缓存激活时触发
  activated() { }
}
</script>
<!-- 组件局部样式（scoped：仅作用于当前组件） -->
<style scoped></style>
